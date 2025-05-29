package ru.grabovsky.poibot.strategy.state

import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.util.CommonUtils.currentStateCode

interface State {
    fun isAvailableForCurrentState(stateCode: StateCode): Boolean {
        return this.currentStateCode( "State") == stateCode
    }

    fun getNextState(user: User, chat: Chat, ): StateCode?
}