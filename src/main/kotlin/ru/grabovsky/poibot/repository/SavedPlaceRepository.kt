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

    fun findByOwnerIdOrderByCreatedAtAscIdAsc(ownerId: Long): List<SavedPlace>

    @Query("select distinct s.placeId from SavedPlace s where s.ownerId = :ownerId")
    fun findPlaceIdsByOwnerId(@Param("ownerId") ownerId: Long): List<Long>

    fun countByOwnerId(ownerId: Long): Long

    fun countByPlaceId(placeId: Long): Long

    fun existsByOwnerIdAndPlaceId(ownerId: Long, placeId: Long): Boolean

    fun findByIdInAndOwnerId(ids: Collection<Long>, ownerId: Long): List<SavedPlace>

    @Query(
        "select s from SavedPlace s where s.id in " +
                "(select c.id.savedPlaceId from SavedPlaceChat c where c.id.chatId = :chatId) " +
                "order by s.createdAt desc, s.id desc",
        countQuery = "select count(c) from SavedPlaceChat c where c.id.chatId = :chatId"
    )
    fun findPublishedInChat(@Param("chatId") chatId: Long, pageable: Pageable): Page<SavedPlace>

    @Query(
        "select s from SavedPlace s where s.id = :id and s.id in " +
                "(select c.id.savedPlaceId from SavedPlaceChat c where c.id.chatId = :chatId)"
    )
    fun findPublishedInChatById(@Param("chatId") chatId: Long, @Param("id") id: Long): SavedPlace?

    @Query(
        "select s from SavedPlace s where s.id in " +
                "(select c.id.savedPlaceId from SavedPlaceChat c where c.id.chatId = :chatId) " +
                "and s.lat between :minLat and :maxLat and s.lon between :minLon and :maxLon"
    )
    fun findPublishedInBox(
        @Param("chatId") chatId: Long,
        @Param("minLat") minLat: Double,
        @Param("maxLat") maxLat: Double,
        @Param("minLon") minLon: Double,
        @Param("maxLon") maxLon: Double,
    ): List<SavedPlace>

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
