package ru.grabovsky.poibot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.User
import ru.grabovsky.poibot.mapper.UserMapper
import ru.grabovsky.poibot.repository.UserRepository
import ru.grabovsky.poibot.service.interfaces.UserService

@Service
class UserServiceImpl(
    private val userRepository: UserRepository
): UserService {

    override fun createOrUpdateUser(user: User) {
        val entity = userRepository.findByUserId(user.id)
        val userFromTelegram= UserMapper.fromTelegramToEntity(user)
        if (userFromTelegram != entity) {
            userRepository.saveAndFlush(userFromTelegram)
            logger.info { "Save user entity with id = ${user.id}" }
        }
    }

    companion object {
        val logger = KotlinLogging.logger {}
    }
}