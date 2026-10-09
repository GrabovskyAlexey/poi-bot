package ru.grabovsky.poibot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.grabovsky.poibot.entity.PlaceShareToken

@Repository
interface PlaceShareTokenRepository : JpaRepository<PlaceShareToken, String> {
    fun findBySavedPlaceId(savedPlaceId: Long): PlaceShareToken?
}
