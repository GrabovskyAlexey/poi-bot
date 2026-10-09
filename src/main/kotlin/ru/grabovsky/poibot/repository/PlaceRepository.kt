package ru.grabovsky.poibot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.grabovsky.poibot.entity.Place

@Repository
interface PlaceRepository : JpaRepository<Place, Long> {

    /** Предфильтр по прямоугольнику; точное расстояние считает вызывающий код. */
    @Query(
        "select p from Place p where p.mergedIntoId is null " +
                "and p.lat between :minLat and :maxLat and p.lon between :minLon and :maxLon"
    )
    fun findInBox(
        @Param("minLat") minLat: Double,
        @Param("maxLat") maxLat: Double,
        @Param("minLon") minLon: Double,
        @Param("maxLon") maxLon: Double,
    ): List<Place>
}
