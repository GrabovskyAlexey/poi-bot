package ru.grabovsky.poibot.strategy.context

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.strategy.state.State
import ru.grabovsky.poibot.strategy.state.StateCode

@Component
class StateContext(
    private val states: List<State>
) {
    fun next(user: User, chat: Chat, stateCode: StateCode) =
        states.firstOrNull { it.isAvailableForCurrentState(stateCode) }?.getNextState(user, chat)
}