package ru.grabovsky.poibot.strategy.flow.core.telegram

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.reactions.SetMessageReaction
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypeEmoji
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.core.templating.FlowTemplateRenderer
import ru.grabovsky.poibot.util.TelegramLogUtils
import java.util.*

@Component
class TelegramFlowActionExecutor(
    private val telegramClient: TelegramClient,
    private val objectMapper: ObjectMapper,
    private val templateRenderer: FlowTemplateRenderer,
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
                    action.bindingKey?.let { replacements[it] = result.messageId }
                }

                is EditMessageAction -> {
                    val messageId = currentBindings[action.bindingKey]
                        ?: error("Message binding ${action.bindingKey} not found for user ${user.id}")
                    val rendered = renderMessage(action.message, locale)
                    val editMessage = buildEditMessage(user.id, messageId, rendered, action.message)
                    logger.debug {
                        "Editing message: userId=${user.id}, flow=${action.message.flowKey}, " +
                        "step=${action.message.stepKey}, bindingKey=${action.bindingKey}, " +
                        TelegramLogUtils.formatEditMessage(editMessage, objectMapper)
                    }
                    telegramClient.execute(editMessage)
                }

                is DeleteMessageAction -> {
                    val messageId = currentBindings[action.bindingKey]
                        ?: return@forEach
                    logger.debug { "Deleting message: userId=${user.id}, messageId=$messageId, bindingKey=${action.bindingKey}" }
                    telegramClient.execute(
                        DeleteMessages.builder()
                            .chatId(user.id)
                            .messageIds(listOf(messageId))
                            .build()
                    )
                    removed += action.bindingKey
                }

                is DeleteMessageIdAction -> {
                    logger.debug { "Deleting message by ID: userId=${user.id}, messageId=${action.messageId}" }
                    telegramClient.execute(
                        DeleteMessages.builder()
                            .chatId(user.id)
                            .messageIds(listOf(action.messageId))
                            .build()
                    )
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

    private fun renderMessage(message: FlowMessage, locale: Locale): String =
        templateRenderer.render(message.flowKey, message.stepKey, locale, message.model)

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
        return message.inlineButtons.takeIf { it.isNotEmpty() }?.let { buildInlineMarkup(it) }
            ?: message.replyButtons.takeIf { it.isNotEmpty() }?.let { replyButtons ->
                val keyboardRows = replyButtons.map { button ->
                    KeyboardRow().apply {
                        add(
                            KeyboardButton.builder()
                                .text(button.text)
                                .requestLocation(button.requestLocation)
                                .build()
                        )
                    }
                }
                ReplyKeyboardMarkup.builder()
                    .keyboard(keyboardRows)
                    .resizeKeyboard(true)
                    .build()
            }
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
                        callbackData = objectMapper.writeValueAsString(button.payload)
                    }
                }
            )
        }
        return InlineKeyboardMarkup(rows)
    }


    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
