package ru.grabovsky.poibot.service

import org.springframework.stereotype.Service
import ru.grabovsky.poibot.service.interfaces.ChatCleanupPolicy
import ru.grabovsky.poibot.service.interfaces.UserService

/** В личке `chatId` совпадает с id пользователя; для остальных чатов (и неизвестных пользователей) удаление включено. */
@Service
class ChatCleanupPolicyImpl(private val userService: UserService) : ChatCleanupPolicy {
    override fun enabled(chatId: Long): Boolean =
        userService.getUser(chatId)?.profile?.settings?.cleanChat ?: true
}
