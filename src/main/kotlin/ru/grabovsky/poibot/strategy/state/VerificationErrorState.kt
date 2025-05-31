package ru.grabovsky.poibot.strategy.state

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.service.interfaces.StateService

@Component
class VerificationErrorState(private val stateService: StateService) : State {
    override fun getNextState(user: User, chat: Chat): StateCode? {
        return stateService.getState(user, chat).verification?.stateCode
    }
}