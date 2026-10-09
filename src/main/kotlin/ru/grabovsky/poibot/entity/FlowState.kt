package ru.grabovsky.poibot.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "flow_state", schema = "poi_bot")
data class FlowState(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "flow_key", nullable = false)
    val flowKey: String,

    @Column(name = "step_key", nullable = false)
    var stepKey: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    var payload: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "message_bindings")
    var messageBindings: MutableMap<String, Int>? = mutableMapOf(),

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant? = null,
)