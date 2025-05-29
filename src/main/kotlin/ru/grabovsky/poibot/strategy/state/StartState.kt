package ru.grabovsky.poibot.strategy.state

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat

@Component
class StartState : State {
    override fun getNextState(user: User, chat: Chat): StateCode? = StateCode.WAITING
}