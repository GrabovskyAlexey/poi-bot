package ru.grabovsky.poibot.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "chat", schema = "poi_bot")
data class Chat(
    @Id
    @Column(name = "id")
    val id: Long,
    @Column(name = "type", nullable = false)
    var type: String,
    @Column(name = "title")
    var title: String? = null,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant? = null,
)
