package ru.grabovsky.poibot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.grabovsky.poibot.dto.InlineMarkupDataDto
import ru.grabovsky.poibot.dto.ReplyMarkupDto
import ru.grabovsky.poibot.event.TelegramStateEvent
import ru.grabovsky.poibot.service.interfaces.StateService
import ru.grabovsky.poibot.service.interfaces.TelegramBotService
import ru.grabovsky.poibot.service.interfaces.VerificationService
import ru.grabovsky.poibot.strategy.context.MessageContext
import ru.grabovsky.poibot.strategy.context.StateContext
import ru.grabovsky.poibot.strategy.dto.DataModel
import ru.grabovsky.poibot.strategy.state.MarkType
import ru.grabovsky.poibot.strategy.state.StateAction.*
import ru.grabovsky.poibot.strategy.state.StateCode

@Service
class TelegramBotServiceImpl(
    private val telegramClient: TelegramClient,
    private val messageContext: MessageContext<DataModel>,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val stateContext: StateContext,
    private val verificationService: VerificationService,
    private val stateService: StateService
) : TelegramBotService {

    override fun processState(user: User, chat: Chat, stateCode: StateCode) {
        when (stateCode.action) {
            SEND_MESSAGE -> sendMessage(user, chat, stateCode)
            VERIFICATION -> verify(user, chat, stateCode)
            UPDATE_MESSAGE -> editMessage(user, chat, stateCode)
            DELETE_MESSAGES -> deleteMessage(user, chat)
            NOTHING -> {}
        }

        if (stateCode.pause) {
            return
        }
        stateContext.next(user, chat, stateCode)?.let { stateCode ->
            applicationEventPublisher.publishEvent(TelegramStateEvent(user, chat, stateCode))
        }
    }

    private fun sendMessage(user: User, chat: Chat, stateCode: StateCode) {
        val result = telegramClient.execute(getSendMessage(user, chat, stateCode))
        val state = stateService.getState(user, chat)
        when(stateCode.markType) {
            MarkType.DELETE -> state.deletedMessages.add(result.messageId)
            MarkType.UPDATE -> state.updateMessageId = result.messageId
            else -> {}
        }
        stateService.saveState(state)
    }

    private fun verify(user: User, chat: Chat, stateCode: StateCode) {
        verificationService.verify(user, chat, stateCode)
    }

    private fun editMessage(user: User, chat: Chat, stateCode: StateCode) {
        val state = stateService.getState(user, chat)
        state.updateMessageId?.let {
            telegramClient.execute(getEditMessage(user, chat, stateCode, it))
        }
        stateService.saveState(state)
    }

    private fun deleteMessage(user: User, chat: Chat) {
        val state = stateService.getState(user, chat)
           if(state.deletedMessages.isNotEmpty()) {
            telegramClient.execute(getDeleteMessages(chat, state.deletedMessages))
        }
        state.deletedMessages.clear()
        stateService.saveState(state)
    }


    private fun getEditMessage(user: User, chat: Chat, stateCode: StateCode, messageId: Int): EditMessageText {
        logger.info { "Get update message for state: $stateCode" }
        val message = messageContext.getMessage(user, chat, stateCode)
            ?: throw IllegalStateException("message is null")

        val markup = message.inlineButtons.getInlineKeyboardMarkup()

        val editMessage = EditMessageText.builder()
            .chatId(chat.id)
            .text(message.message)
            .replyMarkup(markup)
            .messageId(messageId)
            .build()
        editMessage.enableMarkdown(true)
        logger.info { "Edit message: $editMessage" }

        return editMessage
    }

    private fun getDeleteMessages(chat: Chat, messageIds: List<Int>): DeleteMessages {
        return DeleteMessages.builder()
            .chatId(chat.id)
            .messageIds(messageIds)
            .build()
    }

    private fun getSendMessage(user: User, chat: Chat, stateCode: StateCode): SendMessage {
        logger.info { "Get send message for state: $stateCode" }
        val message = messageContext.getMessage(user, chat, stateCode)
            ?: throw IllegalStateException("message is null")

        val markup = message.inlineButtons.getInlineKeyboardMarkup()
            .takeIf { it.keyboard.isNotEmpty() }
            ?: message.replyButtons.takeIf { it.isNotEmpty() }?.getReplyMarkup()
            ?: ReplyKeyboardRemove(true)

        val sendMessage = SendMessage.builder()
            .chatId(chat.id)
            .text(message.message)
            .replyMarkup(markup)
            .build()

        sendMessage.enableMarkdown(true)
        return sendMessage
    }

    private fun List<InlineMarkupDataDto>.getInlineKeyboardMarkup(): InlineKeyboardMarkup {

        var inlineKeyboardButtonsInner: MutableList<InlineKeyboardButton>
        val inlineKeyboardButtons: MutableList<MutableList<InlineKeyboardButton>> = mutableListOf()

        this.groupBy { it.rowPos }.toSortedMap().forEach { entry: Map.Entry<Int, List<InlineMarkupDataDto>> ->
            inlineKeyboardButtonsInner = mutableListOf()
            entry.value.forEach { markup: InlineMarkupDataDto ->
                val button = InlineKeyboardButton(markup.text)
                button.callbackData = markup.data
                inlineKeyboardButtonsInner.add(button)
            }
            inlineKeyboardButtons.add(inlineKeyboardButtonsInner.toMutableList())
        }
        val rows = inlineKeyboardButtons.map { InlineKeyboardRow(it) }
        return InlineKeyboardMarkup(rows)
    }

    private fun List<ReplyMarkupDto>.getReplyMarkup(): ReplyKeyboard {
        val keyboardRows = this.map { rmd ->
            KeyboardRow(
                KeyboardButton.builder()
                    .text(rmd.text)
                    .requestLocation(rmd.requestLocation)
                    .build()
            )
        }
        return ReplyKeyboardMarkup.builder().keyboard(keyboardRows).build()
    }

    companion object {
        val logger = KotlinLogging.logger {}
    }
}