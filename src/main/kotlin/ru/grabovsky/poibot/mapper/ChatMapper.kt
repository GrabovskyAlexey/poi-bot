package ru.grabovsky.poibot.mapper

import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.entity.ChatEntity
import ru.grabovsky.poibot.entity.ChatType

object ChatMapper {
    fun fromTelegramToEntity(chat: Chat) =
        ChatEntity(
            chatId = chat.id,
            firstName = chat.firstName,
            lastName = chat.lastName,
            userName = chat.userName,
            title = chat.title,
            type = ChatType.valueOf(chat.type.uppercase())
        )

}