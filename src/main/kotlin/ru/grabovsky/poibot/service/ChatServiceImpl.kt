package ru.grabovsky.poibot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.mapper.ChatMapper
import ru.grabovsky.poibot.repository.ChatRepository
import ru.grabovsky.poibot.service.interfaces.ChatService

@Service
class ChatServiceImpl(
    private val chatRepository: ChatRepository
): ChatService {

    override fun createOrUpdateChat(chat: Chat) {
        val entity = chatRepository.findByChatId(chat.id)
        val chatFromTelegram= ChatMapper.fromTelegramToEntity(chat)
        if (chatFromTelegram != entity) {
            chatRepository.saveAndFlush(chatFromTelegram)
            logger.info { "Save chat entity with id = ${chat.id}" }
        }
    }

    companion object {
        val logger = KotlinLogging.logger {}
    }
}