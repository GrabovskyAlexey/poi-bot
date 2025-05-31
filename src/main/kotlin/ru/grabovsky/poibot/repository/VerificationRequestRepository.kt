package ru.grabovsky.poibot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.grabovsky.poibot.entity.VerificationRequestEntity
import java.util.*

@Repository
interface VerificationRequestRepository: JpaRepository<VerificationRequestEntity, UUID>