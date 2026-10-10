package ru.grabovsky.poibot.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

/** Запись пользователя о месте: собственное название, описание, фото и т.д. */
@Entity
@Table(name = "saved_place", schema = "poi_bot")
data class SavedPlace(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "owner_id", nullable = false)
    val ownerId: Long,
    @Column(name = "place_id", nullable = false)
    var placeId: Long,
    @Column(name = "name", nullable = false)
    var name: String,
    @Column(name = "address")
    var address: String? = null,
    @Column(name = "description")
    var description: String? = null,
    @Column(name = "website_url")
    var websiteUrl: String? = null,
    @Column(name = "photo_file_id")
    var photoFileId: String? = null,
    @Column(name = "photo_file_unique_id")
    var photoFileUniqueId: String? = null,
    @Column(name = "lat")
    var lat: Double? = null,
    @Column(name = "lon")
    var lon: Double? = null,
    /** Личный статус: [PlaceStatus.name] или null. Не виден другим пользователям. */
    @Column(name = "status")
    var status: String? = null,
    @Column(name = "note")
    var note: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", nullable = false)
    var tags: List<String> = emptyList(),
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant? = null,
) {
    fun hasLocation(): Boolean = lat != null && lon != null

    fun placeStatus(): PlaceStatus? = PlaceStatus.fromCode(status)
}

/** Личная отметка о месте. */
enum class PlaceStatus {
    WANT, BEEN;

    companion object {
        fun fromCode(code: String?): PlaceStatus? = entries.firstOrNull { it.name == code }

        /** Нажатие на кнопку статуса: выбирает его, а повторное нажатие на активный статус снимает. */
        fun toggle(current: PlaceStatus?, target: PlaceStatus?): PlaceStatus? = if (current == target) null else target

        /** Ключ текста кнопки статуса; у активного статуса он с отметкой. */
        fun buttonKey(status: PlaceStatus, active: Boolean): String =
            "buttons.status.${status.name.lowercase()}${if (active) "_on" else ""}"
    }
}
