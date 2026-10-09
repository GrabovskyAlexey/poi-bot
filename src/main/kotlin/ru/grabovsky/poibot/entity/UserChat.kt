package ru.grabovsky.poibot.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant

@Entity
@Table(name = "user_chat", schema = "poi_bot")
data class UserChat(
    @EmbeddedId
    val id: UserChatId,
    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now(),
)

@Embeddable
data class UserChatId(
    @Column(name = "user_id")
    val userId: Long = 0,
    @Column(name = "chat_id")
    val chatId: Long = 0,
) : Serializable
