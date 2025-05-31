package ru.grabovsky.poibot.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.grabovsky.poibot.strategy.state.StateCode
import java.util.*

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
    var state: StateCode,
    @Column(name = "poi_data")
    @JdbcTypeCode(SqlTypes.JSON)
    var poiData: Poi? = null,
    @Column(name = "callback_data")
    var callbackData: String? = null,
    @OneToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
    @JoinColumn(name = "verification_request_id", referencedColumnName = "id")
    var verification: VerificationRequestEntity? = null,
    @Column(name = "update_message_id")
    var updateMessageId: Int? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delete_message_ids")
    val deletedMessages: MutableList<Int> = mutableListOf()
)

data class Poi(
    var name: String? = null,
    var address: String? = null,
    var description: String? = null,
    var location: Point? = null
) {
    @JsonIgnore
    fun isNotEmpty() =
        this.name != null
                || this.address != null
//                || this.photoId != null
                || this.description != null
                || this.location != null
}