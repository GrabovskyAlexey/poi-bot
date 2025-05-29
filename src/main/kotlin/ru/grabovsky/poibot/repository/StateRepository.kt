package ru.grabovsky.poibot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.grabovsky.poibot.entity.StateEntity
import java.util.UUID

@Repository
interface StateRepository: JpaRepository<StateEntity, UUID> {
    fun findByUserIdAndChatId(userId: Long, chatId: Long): StateEntity?
}