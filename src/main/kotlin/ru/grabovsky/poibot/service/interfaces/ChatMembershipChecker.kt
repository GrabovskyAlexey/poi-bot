package ru.grabovsky.poibot.service.interfaces

/** Проверка членства в чате через Telegram Bot API. */
interface ChatMembershipChecker {
    /** true/false, если Telegram ответил; null, если проверить не удалось (бот не админ, сеть и т.п.). */
    fun isMember(chatId: Long, userId: Long): Boolean?

    /** Администратор или создатель чата; при ошибке — false. */
    fun isAdmin(chatId: Long, userId: Long): Boolean
}
