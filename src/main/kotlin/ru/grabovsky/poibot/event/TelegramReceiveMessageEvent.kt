package ru.grabovsky.poibot.event

import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.strategy.state.StateCode

data class TelegramReceiveMessageEvent(
    override val user: User,
    override val chat: Chat,
    override val stateCode: StateCode,
    val message: Message
): TelegramEvent
