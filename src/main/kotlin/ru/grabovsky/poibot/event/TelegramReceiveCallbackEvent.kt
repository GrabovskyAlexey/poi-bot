package ru.grabovsky.poibot.event

import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.strategy.state.StateCode

data class TelegramReceiveCallbackEvent(
    override val user: User,
    override val chat: Chat,
    override val stateCode: StateCode,
    val callback: CallbackQuery
): TelegramEvent