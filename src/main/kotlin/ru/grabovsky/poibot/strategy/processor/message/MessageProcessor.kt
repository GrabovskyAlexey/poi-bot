package ru.grabovsky.poibot.strategy.processor.message

import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.strategy.processor.Processor

interface MessageProcessor: Processor {
    fun execute(user: User, chat: Chat, message: Message)
}