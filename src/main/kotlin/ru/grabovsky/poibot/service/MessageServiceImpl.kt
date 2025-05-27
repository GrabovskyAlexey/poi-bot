package ru.grabovsky.poibot.service

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
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
import ru.grabovsky.poibot.service.interfaces.MessageService
import ru.grabovsky.poibot.strategy.context.MessageContext
import ru.grabovsky.poibot.strategy.context.StateContext
import ru.grabovsky.poibot.strategy.dto.DataModel
import ru.grabovsky.poibot.strategy.state.StateAction.NOTHING
import ru.grabovsky.poibot.strategy.state.StateAction.SEND_MESSAGE
import ru.grabovsky.poibot.strategy.state.StateCode

@Service
class MessageServiceImpl(
    private val telegramClient: TelegramClient,
    private val messageContext: MessageContext<DataModel>,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val stateContext: StateContext
) : MessageService {

    override fun sendMessageToBot(user: User, chat: Chat, stateCode: StateCode) {
        when (stateCode.action) {
            SEND_MESSAGE -> telegramClient.execute(getSendMessage(user, chat, stateCode))
            NOTHING -> {}
        }

        if (stateCode.pause) {
            return
        }
        stateContext.next(user, chat, stateCode)?.let { stateCode ->
            applicationEventPublisher.publishEvent(TelegramStateEvent(user, chat, stateCode))
        }
    }

    private fun getSendMessage(user: User, chat: Chat, stateCode: StateCode): SendMessage {
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

        sendMessage.enableHtml(true)

        return sendMessage
    }

    private fun List<InlineMarkupDataDto>.getInlineKeyboardMarkup(): InlineKeyboardMarkup {

        var inlineKeyboardButtonsInner: MutableList<InlineKeyboardButton>
        val inlineKeyboardButtons: MutableList<MutableList<InlineKeyboardButton>> = mutableListOf()

        this.groupBy { it.rowPos }.toSortedMap().forEach { entry: Map.Entry<Int, List<InlineMarkupDataDto>> ->
            inlineKeyboardButtonsInner = mutableListOf()
            entry.value.forEach { markup: InlineMarkupDataDto ->
                val button = InlineKeyboardButton(markup.text)
                button.callbackData = markup.text
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
                    .requestContact(rmd.requestContact)
                    .build()
            )
        }
        return ReplyKeyboardMarkup.builder().keyboard(keyboardRows).build()
    }


}