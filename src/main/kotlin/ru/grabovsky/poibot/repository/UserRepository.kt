package ru.grabovsky.poibot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.grabovsky.poibot.entity.UserEntity

@Repository
interface UserRepository: JpaRepository<UserEntity, Long> {
    fun findByUserId(userId: Long): UserEntity?
}