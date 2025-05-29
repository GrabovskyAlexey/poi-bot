package ru.grabovsky.poibot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.grabovsky.poibot.entity.PoIEntity
import java.util.UUID

@Repository
interface PoiRepository: JpaRepository<PoIEntity, UUID> {
}