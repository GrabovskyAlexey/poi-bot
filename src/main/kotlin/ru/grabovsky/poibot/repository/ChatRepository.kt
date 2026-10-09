package ru.grabovsky.poibot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.grabovsky.poibot.entity.Chat

@Repository
interface ChatRepository : JpaRepository<Chat, Long> {

    @Modifying
    @Query(
        value = "UPDATE poi_bot.chat SET id = :newId, updated_at = NOW() WHERE id = :oldId",
        nativeQuery = true
    )
    fun changeId(@Param("oldId") oldId: Long, @Param("newId") newId: Long): Int
}
