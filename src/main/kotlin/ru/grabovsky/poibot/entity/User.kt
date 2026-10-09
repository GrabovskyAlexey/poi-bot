package ru.grabovsky.poibot.entity

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import ru.grabovsky.poibot.util.DefaultInstantDeserializer
import java.time.Instant

@Entity
@Table(name = "users", schema = "poi_bot")
data class User(
    @Id
    @Column(name = "id")
    val userId: Long,
    @Column(name = "first_name")
    var firstName: String?,
    @Column(name = "last_name")
    var lastName: String?,
    @Column(name = "username")
    var userName: String?,
    @Column(name = "language")
    var language: String? = null,
    @Column(name = "last_action_at")
    @JsonSerialize(using = InstantSerializer::class)
    @JsonDeserialize(using = DefaultInstantDeserializer::class)
    var lastActionAt: Instant? = null,
    @CreationTimestamp
    @Column(name = "created_at")
    @JsonSerialize(using = InstantSerializer::class)
    @JsonDeserialize(using = DefaultInstantDeserializer::class)
    val createdAt: Instant = Instant.now(),
    @UpdateTimestamp
    @Column(name = "updated_at")
    @JsonSerialize(using = InstantSerializer::class)
    @JsonDeserialize(using = DefaultInstantDeserializer::class)
    val updatedAt: Instant = Instant.now(),
) {
    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true, optional = true)
    var profile: UserProfile? = null

    fun isActive(): Boolean = !(profile?.isBlocked ?: false)

    fun isActiveAndHasUsername(): Boolean = isActive() && userName != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        if (userId != other.userId) return false
        if (firstName != other.firstName) return false
        if (lastName != other.lastName) return false
        if (userName != other.userName) return false
        if (language != other.language) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + (firstName?.hashCode() ?: 0)
        result = 31 * result + (lastName?.hashCode() ?: 0)
        result = 31 * result + (userName?.hashCode() ?: 0)
        result = 31 * result + (language?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "User(userName=$userName, lastName=$lastName, firstName=$firstName, language=$language, lastActionAt=$lastActionAt, userId=$userId, createdAt=$createdAt, updatedAt=$updatedAt)"
    }
}
