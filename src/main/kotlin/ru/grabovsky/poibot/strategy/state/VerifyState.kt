package ru.grabovsky.poibot.strategy.state

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.service.interfaces.StateService

@Component
class VerifyState(private val stateService: StateService) : State {
    override fun getNextState(user: User, chat: Chat): StateCode? {
        val isVerified = stateService.getState(user, chat).verification?.result ?: false
        return if (isVerified) {
            StateCode.VERIFICATION_SUCCESS
        } else {
            StateCode.VERIFICATION_ERROR
        }
    }
}