package ru.grabovsky.poibot.strategy.flow.core.telegram

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.reactions.SetMessageReaction
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.send.SendVenue
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypeEmoji
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButtonRequestChat
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.grabovsky.poibot.service.interfaces.ChatCleanupPolicy
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.core.templating.FlowTemplateRenderer
import ru.grabovsky.poibot.util.TelegramLogUtils
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Component
class TelegramFlowActionExecutor(
    private val telegramClient: TelegramClient,
    private val objectMapper: ObjectMapper,
    private val templateRenderer: FlowTemplateRenderer,
    private val scheduler: ScheduledExecutorService = defaultScheduler(),
    private val cleanup: ChatCleanupPolicy = ChatCleanupPolicy { true },
) : FlowActionExecutor {
    override fun execute(
        user: User,
        locale: Locale,
        currentBindings: Map<String, Int>,
        actions: List<FlowAction>,
    ): FlowBindingsMutation {
        if (actions.isEmpty()) {
            return FlowBindingsMutation()
        }
        val replacements = mutableMapOf<String, Int>()
        val removed = mutableSetOf<String>()

        actions.forEach { action ->
            when (action) {
                is SendMessageAction -> {
                    val rendered = renderMessage(action.message, locale)
                    val sendMessage = buildSendMessage(user.id, rendered, action.message)
                    logger.debug {
                        "Sending message: userId=${user.id}, flow=${action.message.flowKey}, " +
                        "step=${action.message.stepKey}, bindingKey=${action.bindingKey}, " +
                        TelegramLogUtils.formatSendMessage(sendMessage, objectMapper)
                    }
                    val result = telegramClient.execute(sendMessage)
                    logger.debug { "Message sent: messageId=${result.messageId}, userId=${user.id}" }
                    action.bindingKey?.let { bind(replacements, removed, it, result.messageId) }
                    scheduleDeletion(user.id, result.messageId, action.message)
                }

                is EditMessageAction -> {
                    val messageId = replacements[action.bindingKey] ?: currentBindings[action.bindingKey]
                        ?: error("Message binding ${action.bindingKey} not found for user ${user.id}")
                    val rendered = renderMessage(action.message, locale)
                    val editMessage = buildEditMessage(user.id, messageId, rendered, action.message)
                    logger.debug {
                        "Editing message: userId=${user.id}, flow=${action.message.flowKey}, " +
                        "step=${action.message.stepKey}, bindingKey=${action.bindingKey}, " +
                        TelegramLogUtils.formatEditMessage(editMessage, objectMapper)
                    }
                    executeEdit(editMessage)
                    scheduleDeletion(user.id, messageId, action.message)
                }

                is DeleteMessageAction -> {
                    val messageId = replacements[action.bindingKey] ?: currentBindings[action.bindingKey]
                        ?: return@forEach
                    logger.debug { "Deleting message: userId=${user.id}, messageId=$messageId, bindingKey=${action.bindingKey}" }
                    telegramClient.execute(
                        DeleteMessages.builder()
                            .chatId(user.id)
                            .messageIds(listOf(messageId))
                            .build()
                    )
                    removed += action.bindingKey
                    replacements.remove(action.bindingKey)
                }

                is DeleteMessageIdAction -> {
                    logger.debug { "Deleting message by ID: userId=${user.id}, messageId=${action.messageId}" }
                    runCatching {
                        telegramClient.execute(
                            DeleteMessages.builder()
                                .chatId(user.id)
                                .messageIds(listOf(action.messageId))
                                .build()
                        )
                    }.onFailure {
                        logger.debug { "Could not delete message ${action.messageId}: ${it.message}" }
                    }
                }

                is AnswerCallbackAction -> {
                    logger.debug {
                        "Answering callback: callbackId=${action.callbackQueryId}, " +
                        "text='${action.text}', showAlert=${action.showAlert}"
                    }
                    telegramClient.execute(
                        AnswerCallbackQuery.builder()
                            .callbackQueryId(action.callbackQueryId)
                            .text(action.text)
                            .showAlert(action.showAlert)
                            .build()
                    )
                }

                is EditCardAction -> {
                    val rendered = renderMessage(action.message, locale)
                    val markup = buildInlineMarkup(action.message.inlineButtons)
                    runCatching {
                        if (action.caption) {
                            val edit = EditMessageCaption.builder().chatId(user.id).messageId(action.messageId)
                                .caption(rendered.take(MAX_CAPTION_LENGTH)).build()
                            action.message.parseMode.telegramValue?.let { edit.parseMode = it }
                            edit.replyMarkup = markup
                            telegramClient.execute(edit)
                        } else {
                            val edit = buildEditMessage(user.id, action.messageId, rendered, action.message)
                            telegramClient.execute(edit)
                        }
                    }.onFailure {
                        // карточку могли удалить или она не изменилась — для flow это не ошибка
                        logger.debug { "Card refresh skipped for message ${action.messageId}: ${it.message}" }
                    }
                }

                is SendPhotoAction -> {
                    val caption = renderMessage(action.message, locale).take(MAX_CAPTION_LENGTH)
                    val sendPhoto = SendPhoto.builder()
                        .chatId(user.id)
                        .photo(InputFile(action.photoFileId))
                        .caption(caption)
                        .build()
                    action.message.parseMode.telegramValue?.let { sendPhoto.parseMode = it }
                    sendPhoto.replyMarkup = buildInlineMarkup(action.message.inlineButtons)
                    val result = telegramClient.execute(sendPhoto)
                    action.bindingKey?.let { bind(replacements, removed, it, result.messageId) }
                }

                is SendDocumentAction -> {
                    val sendDocument = SendDocument.builder()
                        .chatId(user.id)
                        .document(InputFile(java.io.ByteArrayInputStream(action.content), action.fileName))
                        .build()
                    val result = telegramClient.execute(sendDocument)
                    action.bindingKey?.let { bind(replacements, removed, it, result.messageId) }
                }

                is SendVenueAction -> {
                    val sendVenue = SendVenue.builder()
                        .chatId(user.id)
                        .latitude(action.latitude)
                        .longitude(action.longitude)
                        .title(action.title)
                        .address(action.address ?: "")
                        .build()
                    val result = telegramClient.execute(sendVenue)
                    action.bindingKey?.let { bind(replacements, removed, it, result.messageId) }
                }

                is SetReactionAction -> {
                    if (action.messageId > 0) {
                        val reaction = ReactionTypeEmoji.builder()
                            .emoji(action.emoji)
                            .build()
                        runCatching {
                            val request = SetMessageReaction.builder()
                                .chatId(action.chatId.toString())
                                .messageId(action.messageId)
                                .reactionTypes(listOf(reaction))
                                .isBig(false)
                                .build()
                            telegramClient.execute(request)
                        }.onFailure {
                            logger.warn { "Failed to set reaction ${action.emoji} for chat ${action.chatId}, message ${action.messageId}: ${it.message}" }
                        }
                    }
                }
            }
        }

        return FlowBindingsMutation(
            replacements = replacements,
            removed = removed,
        )
    }

    /** Временные сообщения (подтверждения) удаляются сами, чтобы не засорять чат. */
    private fun scheduleDeletion(chatId: Long, messageId: Int, message: FlowMessage) {
        val seconds = message.autoDeleteAfterSeconds ?: return
        if (!cleanup.enabled(chatId)) return
        scheduler.schedule({
            runCatching {
                telegramClient.execute(DeleteMessages.builder().chatId(chatId).messageIds(listOf(messageId)).build())
            }.onFailure { logger.debug { "Could not auto-delete message $messageId: ${it.message}" } }
        }, seconds.toLong(), TimeUnit.SECONDS)
    }

    /** Новая привязка отменяет удаление того же ключа в этом пакете действий (удалить старое + отправить новое). */
    private fun bind(replacements: MutableMap<String, Int>, removed: MutableSet<String>, key: String, messageId: Int) {
        replacements[key] = messageId
        removed.remove(key)
    }

    /** Telegram отвечает 400, если содержимое не изменилось; для флоу это не ошибка. */
    private fun executeEdit(editMessage: EditMessageText) {
        try {
            telegramClient.execute(editMessage)
        } catch (error: TelegramApiRequestException) {
            if (error.apiResponse?.contains("message is not modified") != true) throw error
            logger.debug { "Edit skipped: message is not modified" }
        }
    }

    private fun renderMessage(message: FlowMessage, locale: Locale): String =
        templateRenderer.render(message.flowKey, message.stepKey, message.locale ?: locale, message.model)

    private fun buildSendMessage(chatId: Long, text: String, message: FlowMessage): SendMessage {
        val sendMessage = SendMessage.builder()
            .chatId(chatId)
            .text(text)
            .build()

        message.parseMode.telegramValue?.let { sendMessage.parseMode = it }
        message.replyToMessageId?.let { sendMessage.replyToMessageId = it }
        sendMessage.replyMarkup = buildReplyMarkup(message)
        return sendMessage
    }

    private fun buildEditMessage(chatId: Long, messageId: Int, text: String, message: FlowMessage): EditMessageText {
        val editMessage = EditMessageText.builder()
            .chatId(chatId)
            .messageId(messageId)
            .text(text)
            .build()

        message.parseMode.telegramValue?.let { editMessage.parseMode = it }
        editMessage.replyMarkup = buildInlineMarkup(message.inlineButtons)
        return editMessage
    }

    private fun buildReplyMarkup(message: FlowMessage): ReplyKeyboard? {
        val keyboard = buildKeyboard(message)
        return keyboard ?: if (message.removeReplyKeyboard) ReplyKeyboardRemove(true) else null
    }

    private fun buildKeyboard(message: FlowMessage): ReplyKeyboard? {
        return message.inlineButtons.takeIf { it.isNotEmpty() }?.let { buildInlineMarkup(it) }
            ?: message.replyButtons.takeIf { it.isNotEmpty() }?.let { replyButtons ->
                val keyboardRows = replyButtons.map { button ->
                    KeyboardRow().apply {
                        add(
                            KeyboardButton.builder()
                                .text(button.text)
                                // Поля типа кнопки взаимоисключающие: false тоже считается заданным, поэтому передаём только true
                                .requestLocation(button.requestLocation.takeIf { it })
                                .build()
                                .also { keyboardButton ->
                                    button.requestChatId?.let { keyboardButton.requestChat = groupPickerRequest(it) }
                                }
                        )
                    }
                }
                ReplyKeyboardMarkup.builder()
                    .keyboard(keyboardRows)
                    .resizeKeyboard(true)
                    .build()
            }
    }

    private fun groupPickerRequest(requestId: String) =
        KeyboardButtonRequestChat(requestId, false).apply {
            botIsMember = true
            requestTitle = true
        }

    private fun buildInlineMarkup(buttons: List<FlowInlineButton>): InlineKeyboardMarkup? {
        if (buttons.isEmpty()) {
            return null
        }
        val grouped = buttons.groupBy { it.row }.toSortedMap()
        val rows = grouped.map { (_, rowButtons) ->
            val sorted = rowButtons.sortedBy { it.col }
            InlineKeyboardRow(
                sorted.map { button ->
                    InlineKeyboardButton(button.text).apply {
                        if (button.url != null) {
                            url = button.url
                        } else {
                            callbackData = objectMapper.writeValueAsString(button.payload)
                        }
                    }
                }
            )
        }
        return InlineKeyboardMarkup(rows)
    }


    companion object {
        private val logger = KotlinLogging.logger {}
        private const val MAX_CAPTION_LENGTH = 1024

        private fun defaultScheduler(): ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "flow-auto-delete").apply { isDaemon = true }
            }
    }
}
