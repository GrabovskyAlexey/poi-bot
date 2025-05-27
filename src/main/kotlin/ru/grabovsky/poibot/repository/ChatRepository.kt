package ru.grabovsky.poibot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.grabovsky.poibot.entity.ChatEntity

@Repository
interface ChatRepository: JpaRepository<ChatEntity, Long> {
    fun findByChatId(chatId: Long): ChatEntity?
}