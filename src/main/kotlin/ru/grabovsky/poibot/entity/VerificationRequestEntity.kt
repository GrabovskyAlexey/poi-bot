package ru.grabovsky.poibot.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.telegram.telegrambots.meta.api.objects.Location
import org.telegram.telegrambots.meta.api.objects.PhotoSize
import ru.grabovsky.poibot.strategy.state.StateCode
import java.util.*

@Entity
@Table(name="verification_request", schema = "poi_bot")
data class VerificationRequestEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @JdbcTypeCode(SqlTypes.JSON)
    var message: TelegramMessageDto,
    @Column(name = "state")
    @Enumerated(EnumType.STRING)
    var stateCode: StateCode,
    var result: Boolean = false
)

data class TelegramMessageDto(
    val text: String? = null,
    val photo: List<PhotoSize>? = null,
    val location: Location? = null
)