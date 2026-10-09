package ru.grabovsky.poibot.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.io.Serializable
import java.time.Instant

@Embeddable
data class PlaceRatingId(
    @Column(name = "place_id")
    val placeId: Long = 0,
    @Column(name = "user_id")
    val userId: Long = 0,
) : Serializable

/** Оценка места пользователем (1..5). Привязана к [Place], а не к записи, поэтому общая для всех, кто сохранил место. */
@Entity
@Table(name = "place_rating", schema = "poi_bot")
data class PlaceRating(
    @EmbeddedId
    val id: PlaceRatingId,
    @Column(name = "value", nullable = false)
    var value: Int,
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant? = null,
)

/** Комментарий к месту: виден всем анонимно, автор хранится для удаления и модерации. */
@Entity
@Table(name = "place_comment", schema = "poi_bot")
data class PlaceComment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "place_id", nullable = false)
    var placeId: Long,
    @Column(name = "author_id", nullable = false)
    val authorId: Long,
    @Column(name = "text", nullable = false)
    var text: String,
    @Column(name = "hidden", nullable = false)
    var hidden: Boolean = false,
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,
)

@Embeddable
data class PlaceCommentReportId(
    @Column(name = "comment_id")
    val commentId: Long = 0,
    @Column(name = "reporter_id")
    val reporterId: Long = 0,
) : Serializable

@Entity
@Table(name = "place_comment_report", schema = "poi_bot")
data class PlaceCommentReport(
    @EmbeddedId
    val id: PlaceCommentReportId,
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,
)
