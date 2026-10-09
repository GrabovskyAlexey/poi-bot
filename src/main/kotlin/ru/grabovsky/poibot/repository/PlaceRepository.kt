package ru.grabovsky.poibot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
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

    /** Удаляет из [ids] места без записей, оценок, комментариев и ссылок слияния (после удаления пользователя). */
    @Modifying
    @Query(
        value = "delete from poi_bot.place p where p.id in (:ids) " +
                "and not exists (select 1 from poi_bot.saved_place s where s.place_id = p.id) " +
                "and not exists (select 1 from poi_bot.place_rating r where r.place_id = p.id) " +
                "and not exists (select 1 from poi_bot.place_comment c where c.place_id = p.id) " +
                "and not exists (select 1 from poi_bot.place m where m.merged_into_id = p.id)",
        nativeQuery = true,
    )
    fun deleteUnused(@Param("ids") ids: Collection<Long>): Int
}
