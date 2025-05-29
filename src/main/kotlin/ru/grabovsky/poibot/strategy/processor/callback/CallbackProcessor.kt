package ru.grabovsky.poibot.strategy.processor.callback

import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.strategy.processor.Processor


interface CallbackProcessor: Processor {
    fun execute(user: User, chat: Chat, callbackQuery: CallbackQuery): ExecuteStatus
}