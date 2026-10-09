package ru.grabovsky.poibot.service.interfaces

import ru.grabovsky.poibot.entity.User
import org.telegram.telegrambots.meta.api.objects.User as TgUser

interface UserService {
    fun createOrUpdateUser(user: TgUser): User
    fun saveUser(user: User)
    fun getUser(userId: Long): User?
    fun updateBlockedStatus(userId: Long, isBlocked: Boolean)
    fun findByUsername(username: String): User?
}
