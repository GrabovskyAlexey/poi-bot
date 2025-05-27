package ru.grabovsky.poibot.service.interfaces

import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.strategy.state.StateCode

interface MessageService {
    fun sendMessageToBot(user: User, chat: Chat, stateCode: StateCode)
}