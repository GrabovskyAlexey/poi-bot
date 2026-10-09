package ru.grabovsky.poibot.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/**
 * Физическое место (одно заведение в реальности). К нему привязываются записи пользователей
 * ([SavedPlace]); в будущем — рейтинг и комментарии.
 */
@Entity
@Table(name = "place", schema = "poi_bot")
data class Place(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "lat")
    var lat: Double? = null,
    @Column(name = "lon")
    var lon: Double? = null,
    @Column(name = "display_name", nullable = false)
    var displayName: String,
    @Column(name = "address")
    var address: String? = null,
    @Column(name = "merged_into_id")
    var mergedIntoId: Long? = null,
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,
)
