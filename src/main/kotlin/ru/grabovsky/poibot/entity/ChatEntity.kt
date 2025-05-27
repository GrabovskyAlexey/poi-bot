package ru.grabovsky.poibot.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "chat", schema = "poi_bot")
data class ChatEntity(
    @Id
    @Column(name = "chat_id")
    val chatId: Long,
    @Column(name = "first_name")
    val firstName: String?,
    @Column(name = "last_name")
    val lastName: String?,
    @Column(name = "username")
    val userName: String?,
    @Column(name = "title")
    val title: String?,
    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    val type: ChatType
)