package ru.grabovsky.poibot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import ru.grabovsky.poibot.entity.User
import ru.grabovsky.poibot.entity.UserProfile
import ru.grabovsky.poibot.mapper.UserMapper
import ru.grabovsky.poibot.repository.UserRepository
import ru.grabovsky.poibot.service.interfaces.UserService
import java.time.Instant
import org.telegram.telegrambots.meta.api.objects.User as TgUser

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
) : UserService {

    override fun createOrUpdateUser(user: TgUser): User {
        val entity = userRepository.findUserByUserId(user.id)
        return entity?.let { updateUser(it, user) }
            ?: createNewUser(UserMapper.fromTelegramToEntity(user))
    }

    private fun updateUser(entity: User, user: TgUser): User {
        val profile = entity.profile ?: UserProfile(user = entity).also { entity.profile = it }
        profile.isBlocked = false
        entity.firstName = user.firstName
        entity.lastName = user.lastName
        entity.userName = user.userName
        entity.language = user.languageCode
        entity.lastActionAt = Instant.now()
        return userRepository.saveAndFlush(entity)
    }

    private fun createNewUser(userFromTelegram: User): User {
        logger.info {
            "Creating new user: userId=${userFromTelegram.userId}, username=${userFromTelegram.userName}"
        }
        userFromTelegram.apply {
            lastActionAt = Instant.now()
            profile = UserProfile(user = this)
        }
        return userRepository.saveAndFlush(userFromTelegram)
    }

    override fun saveUser(user: User) {
        user.profile?.user = user
        userRepository.saveAndFlush(user)
    }

    override fun getUser(userId: Long): User? {
        val user = userRepository.findUserByUserId(userId) ?: return null
        user.profile?.user = user
        return user
    }

    override fun updateBlockedStatus(userId: Long, isBlocked: Boolean) {
        val user = userRepository.findUserByUserId(userId)
        if (user == null) {
            logger.info { "Skip update blocked status for unknown user $userId" }
            return
        }
        val profile = user.profile ?: UserProfile(user = user).also { user.profile = it }
        if (profile.isBlocked == isBlocked) {
            return
        }
        profile.isBlocked = isBlocked
        userRepository.saveAndFlush(user)
        logger.info { "User $userId blocked status updated to $isBlocked" }
    }

    override fun findByUsername(username: String): User? =
        userRepository.findByUserNameIgnoreCase(username)

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
