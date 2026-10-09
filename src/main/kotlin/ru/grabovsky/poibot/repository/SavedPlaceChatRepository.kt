package ru.grabovsky.poibot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.grabovsky.poibot.entity.SavedPlaceChat
import ru.grabovsky.poibot.entity.SavedPlaceChatId

/** Сколько из выбранных записей опубликовано в чате. */
interface ChatPublishCount {
    val chatId: Long
    val total: Long
}

@Repository
interface SavedPlaceChatRepository : JpaRepository<SavedPlaceChat, SavedPlaceChatId> {

    @Query(
        "select c.id.chatId as chatId, count(c) as total from SavedPlaceChat c " +
                "where c.id.savedPlaceId in :placeIds and c.id.chatId in :chatIds group by c.id.chatId"
    )
    fun countByChats(
        @Param("placeIds") placeIds: Collection<Long>,
        @Param("chatIds") chatIds: Collection<Long>,
    ): List<ChatPublishCount>
}
