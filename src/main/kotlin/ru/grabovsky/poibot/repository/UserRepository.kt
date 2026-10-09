package ru.grabovsky.poibot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.grabovsky.poibot.entity.User

@Repository
interface UserRepository: JpaRepository<User, Long> {
    fun findUserByUserId(userId: Long): User?
    fun findByUserNameIgnoreCase(userName: String): User?

    @Query("select u from User u left join fetch u.profile p where p is null or p.isBlocked=false")
    fun findAllNotBlockedUser(): List<User>
}
