package ru.grabovsky.poibot.strategy.processor.message

import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.entity.TelegramMessageDto
import ru.grabovsky.poibot.entity.VerificationRequestEntity
import ru.grabovsky.poibot.service.interfaces.StateService

abstract class MessageVerificationProcessor(private val stateService: StateService) : MessageProcessor {
    override fun execute(user: User, chat: Chat, message: Message) {
        verify(user, chat, message)
    }

    fun verify(user: User, chat: Chat, message: Message) {
        val state = stateService.getState(user, chat)
        val messageDto = TelegramMessageDto(message.text, message.photo, message.location)
        state.verification = state.verification?.apply{
            this.message = messageDto
            this.stateCode = state.state
        } ?: VerificationRequestEntity(message = messageDto, stateCode = state.state)
        stateService.saveState(state)
    }
}