package ru.grabovsky.poibot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.grabovsky.poibot.entity.Chat
import ru.grabovsky.poibot.entity.UserChat
import ru.grabovsky.poibot.entity.UserChatId

@Repository
interface UserChatRepository : JpaRepository<UserChat, UserChatId> {

    @Query(
        "select c from Chat c join UserChat uc on uc.id.chatId = c.id " +
                "where uc.id.userId = :userId and c.isActive = true order by c.title"
    )
    fun findActiveChatsByUserId(@Param("userId") userId: Long): List<Chat>
}
