package ru.grabovsky.poibot.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.io.Serializable
import java.time.Instant

/** Публикация записи пользователя в группу: запись видна участникам группы по командам бота. */
@Entity
@Table(name = "saved_place_chat", schema = "poi_bot")
data class SavedPlaceChat(
    @EmbeddedId
    val id: SavedPlaceChatId,
    @Column(name = "shared_by", nullable = false)
    val sharedBy: Long,
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,
)

@Embeddable
data class SavedPlaceChatId(
    @Column(name = "saved_place_id")
    val savedPlaceId: Long = 0,
    @Column(name = "chat_id")
    val chatId: Long = 0,
) : Serializable
