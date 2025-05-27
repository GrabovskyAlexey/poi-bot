package ru.grabovsky.poibot.service.interfaces

import org.telegram.telegrambots.meta.api.objects.User

interface UserService {
    fun createOrUpdateUser(user: User)
}