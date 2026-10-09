package ru.grabovsky.poibot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.grabovsky.poibot.entity.Chat
import ru.grabovsky.poibot.entity.UserChat
import ru.grabovsky.poibot.entity.UserChatId
import ru.grabovsky.poibot.repository.ChatRepository
import ru.grabovsky.poibot.repository.UserChatRepository
import ru.grabovsky.poibot.service.interfaces.ChatService
import java.time.Instant
import org.telegram.telegrambots.meta.api.objects.chat.Chat as TgChat

@Service
class ChatServiceImpl(
    private val chatRepository: ChatRepository,
    private val userChatRepository: UserChatRepository,
) : ChatService {

    @Transactional
    override fun registerChat(chat: TgChat): Chat =
        upsert(chat.id, chat.type, chat.title, overrideType = true)

    @Transactional
    override fun registerSharedChat(chatId: Long, title: String?): Chat =
        upsert(chatId, DEFAULT_SHARED_TYPE, title, overrideType = false)

    private fun upsert(chatId: Long, type: String, title: String?, overrideType: Boolean): Chat {
        val existing = chatRepository.findById(chatId).orElse(null)
        if (existing == null) {
            logger.info { "Register chat id=$chatId type=$type title=$title" }
            return chatRepository.save(Chat(id = chatId, type = type, title = title))
        }
        if (overrideType) existing.type = type
        title?.let { existing.title = it }
        return chatRepository.save(existing)
    }

    @Transactional
    override fun linkUser(userId: Long, chatId: Long) {
        val id = UserChatId(userId, chatId)
        val link = userChatRepository.findById(id).orElse(null)
        if (link == null) {
            userChatRepository.save(UserChat(id))
        } else {
            link.lastSeenAt = Instant.now()
            userChatRepository.save(link)
        }
    }

    @Transactional
    override fun unlinkUser(userId: Long, chatId: Long) {
        userChatRepository.deleteById(UserChatId(userId, chatId))
    }

    @Transactional
    override fun setActive(chatId: Long, active: Boolean) {
        val chat = chatRepository.findById(chatId).orElse(null) ?: return
        if (chat.isActive == active) return
        chat.isActive = active
        chatRepository.save(chat)
        logger.info { "Chat $chatId active=$active" }
    }

    @Transactional
    override fun migrate(oldChatId: Long, newChatId: Long) {
        if (!chatRepository.existsById(oldChatId)) return
        if (chatRepository.existsById(newChatId)) {
            logger.warn { "Chat migration $oldChatId -> $newChatId skipped: target already exists" }
            return
        }
        chatRepository.changeId(oldChatId, newChatId)
        logger.info { "Chat migrated $oldChatId -> $newChatId" }
    }

    @Transactional(readOnly = true)
    override fun getUserChats(userId: Long): List<Chat> =
        userChatRepository.findActiveChatsByUserId(userId)

    companion object {
        private const val DEFAULT_SHARED_TYPE = "group"
        private val logger = KotlinLogging.logger {}
    }
}
