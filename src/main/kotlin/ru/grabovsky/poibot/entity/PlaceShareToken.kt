package ru.grabovsky.poibot.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/** Ссылка-приглашение на запись пользователя: по токену любой пользователь бота может сохранить копию себе. */
@Entity
@Table(name = "place_share_token", schema = "poi_bot")
data class PlaceShareToken(
    @Id
    @Column(name = "token")
    val token: String,
    @Column(name = "saved_place_id", nullable = false)
    val savedPlaceId: Long,
    @Column(name = "created_by", nullable = false)
    val createdBy: Long,
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,
)
