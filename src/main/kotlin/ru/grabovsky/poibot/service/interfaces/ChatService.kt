package ru.grabovsky.poibot.service.interfaces

import ru.grabovsky.poibot.entity.Chat
import org.telegram.telegrambots.meta.api.objects.chat.Chat as TgChat

interface ChatService {
    /** Создаёт или обновляет чат (группу), где присутствует бот. */
    fun registerChat(chat: TgChat): Chat

    /** Регистрирует чат по данным `chat_shared` (тип неизвестен, считаем группой). */
    fun registerSharedChat(chatId: Long, title: String?): Chat

    /** Связывает пользователя с чатом: пользователь состоит в группе, где есть бот. */
    fun linkUser(userId: Long, chatId: Long)

    fun unlinkUser(userId: Long, chatId: Long)

    fun setActive(chatId: Long, active: Boolean)

    /** Группа стала супергруппой: меняем id чата, ссылки обновляются каскадом. */
    fun migrate(oldChatId: Long, newChatId: Long)

    /** Активные группы пользователя, в которых есть бот. */
    fun getUserChats(userId: Long): List<Chat>
}
