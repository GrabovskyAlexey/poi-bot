package ru.grabovsky.poibot.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "user", schema = "poi_bot")
data class UserEntity(
    @Id
    @Column("user_id")
    val userId: Long,
    @Column(name = "first_name")
    var firstName: String?,
    @Column(name = "last_name")
    var lastName: String?,
    @Column(name = "username")
    var userName: String?,
    @Column("language")
    var languageCode: String = "ru",
    @Column("is_premium")
    var isPremium: Boolean = false
)