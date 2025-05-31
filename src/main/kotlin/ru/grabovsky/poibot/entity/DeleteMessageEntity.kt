package ru.grabovsky.poibot.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "deleted_message", schema = "poi_bot")
data class DeleteMessageEntity(
    @Id
    @Column(name = "id")
    val chatId: UUID = UUID.randomUUID(),
    @Column(name = "state_id")
    val stateId: UUID,
    @Column(name = "message_id")
    val messageId: Int,
)