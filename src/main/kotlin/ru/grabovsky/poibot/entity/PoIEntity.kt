package ru.grabovsky.poibot.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "poi", schema = "poi_bot")
data class PoIEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "chat_id")
    val chatId: Long,
    @Column(name = "user_id")
    val userId: Long,
    @Column(name = "name")
    var name: String? = null,
//    @Column(name = "photo_id")
//    var photoId: String? = null, TODO реализовать после решения проблем с созранением фото
    @Column(name = "address")
    var address: String? = null,
    @Column(name = "description")
    var description: String? = null,
    @Embedded
    var location: Point? = null
) {
    fun isNotEmpty() =
        this.name != null
                || this.address != null
//                || this.photoId != null
                || this.description != null
                || this.location != null
}
@Embeddable
data class Point(val latitude: BigDecimal, val longitude: BigDecimal)