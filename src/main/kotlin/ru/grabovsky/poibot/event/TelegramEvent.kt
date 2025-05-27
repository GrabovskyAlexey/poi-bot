package ru.grabovsky.poibot.event

import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.strategy.state.StateCode

interface TelegramEvent {
    val user: User
    val chat: Chat
    val stateCode: StateCode
}