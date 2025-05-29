package ru.grabovsky.poibot.mapper

import org.telegram.telegrambots.meta.api.objects.User
import ru.grabovsky.poibot.entity.UserEntity

object UserMapper {
    fun fromTelegramToEntity(user: User) =
        UserEntity(
            userId = user.id,
            firstName = user.firstName,
            lastName = user.lastName,
            userName = user.userName,
            languageCode = user.languageCode,
            isPremium = user.isPremium
        )

}