package ru.grabovsky.poibot.service.interfaces

import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.entity.StateEntity
import ru.grabovsky.poibot.strategy.state.StateCode

interface StateService {
    fun updateState(user: User, chat: Chat, code: StateCode, callbackData: String? = null)
    fun getState(user: User, chat: Chat): StateEntity
    fun saveState(state: StateEntity): StateEntity
}