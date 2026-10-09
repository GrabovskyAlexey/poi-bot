package ru.grabovsky.poibot.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.grabovsky.poibot.entity.SavedPlace

@Repository
interface SavedPlaceRepository : JpaRepository<SavedPlace, Long> {

    fun findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId: Long, pageable: Pageable): Page<SavedPlace>

    fun findByIdAndOwnerId(id: Long, ownerId: Long): SavedPlace?

    fun countByOwnerId(ownerId: Long): Long

    fun countByPlaceId(placeId: Long): Long

    /** Предфильтр по прямоугольнику; точное расстояние считает вызывающий код. */
    @Query(
        "select s from SavedPlace s where s.ownerId = :ownerId " +
                "and s.lat between :minLat and :maxLat and s.lon between :minLon and :maxLon"
    )
    fun findOwnInBox(
        @Param("ownerId") ownerId: Long,
        @Param("minLat") minLat: Double,
        @Param("maxLat") maxLat: Double,
        @Param("minLon") minLon: Double,
        @Param("maxLon") maxLon: Double,
    ): List<SavedPlace>
}
