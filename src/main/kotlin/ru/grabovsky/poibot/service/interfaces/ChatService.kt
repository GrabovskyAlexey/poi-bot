package ru.grabovsky.poibot.service.interfaces

import org.telegram.telegrambots.meta.api.objects.chat.Chat

interface ChatService {
    fun createOrUpdateChat(chat: Chat)
}