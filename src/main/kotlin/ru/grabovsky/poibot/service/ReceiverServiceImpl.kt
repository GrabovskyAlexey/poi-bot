package ru.grabovsky.poibot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.event.TelegramReceiveCallbackEvent
import ru.grabovsky.poibot.event.TelegramReceiveMessageEvent
import ru.grabovsky.poibot.service.interfaces.ReceiverService
import ru.grabovsky.poibot.service.interfaces.StateService

@Service
class ReceiverServiceImpl(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val stateService: StateService
) : ReceiverService {
    override fun execute(update: Update) {
        when {
            update.hasCallbackQuery() -> processCallback(update.callbackQuery)
            update.hasMessage() -> processMessage(update.message)
        }
    }

    private fun processMessage(message: Message) {
        val user = message.from
        val chat = message.chat
        val stateCode = getState(user, chat)
        applicationEventPublisher.publishEvent(
            TelegramReceiveMessageEvent(user, chat, stateCode, message)
        )
    }

    private fun processCallback(callbackQuery: CallbackQuery) {
        val user = callbackQuery.from
        val chat = callbackQuery.message.chat
        val stateCode = getState(user, chat)
        applicationEventPublisher.publishEvent(
            TelegramReceiveCallbackEvent(user, chat, stateCode, callbackQuery)
        )
    }

    private fun getState(user: User, chat: Chat) =
        requireNotNull(stateService.getState(user, chat)?.state) {"State code must not be null"}

    companion object {
        val logger = KotlinLogging.logger {}
    }
}