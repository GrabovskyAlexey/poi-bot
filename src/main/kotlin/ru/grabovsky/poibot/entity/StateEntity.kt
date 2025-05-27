package ru.grabovsky.poibot.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import ru.grabovsky.poibot.strategy.state.StateCode
import java.util.UUID

@Entity
@Table(name="state", schema = "poi_bot")
data class StateEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "chat_id")
    val chatId: Long,
    @Column(name = "user_id")
    val userId: Long,
    @Column(name = "state")
    @Enumerated(EnumType.STRING)
    var state: StateCode? = null,
    @Column(name = "callback_data")
    var callbackData: String? = null
)