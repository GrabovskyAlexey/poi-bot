package ru.grabovsky.poibot.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.grabovsky.poibot.entity.*

interface RatingAggregate {
    val average: Double?
    val total: Long
}

interface PlaceRatingAggregate {
    val placeId: Long
    val average: Double?
    val total: Long
}

@Repository
interface PlaceRatingRepository : JpaRepository<PlaceRating, PlaceRatingId> {

    @Query("select avg(r.value) as average, count(r) as total from PlaceRating r where r.id.placeId = :placeId")
    fun aggregate(@Param("placeId") placeId: Long): RatingAggregate

    @Query(
        "select r.id.placeId as placeId, avg(r.value) as average, count(r) as total from PlaceRating r " +
                "where r.id.placeId in :placeIds group by r.id.placeId"
    )
    fun aggregates(@Param("placeIds") placeIds: Collection<Long>): List<PlaceRatingAggregate>

    @Query("select r from PlaceRating r where r.id.placeId = :placeId")
    fun findAllByPlaceId(@Param("placeId") placeId: Long): List<PlaceRating>
}

@Repository
interface PlaceCommentRepository : JpaRepository<PlaceComment, Long> {

    fun findByPlaceIdAndHiddenFalseOrderByCreatedAtDescIdDesc(placeId: Long, pageable: Pageable): Page<PlaceComment>

    fun countByPlaceIdAndHiddenFalse(placeId: Long): Long

    fun findByPlaceIdAndAuthorId(placeId: Long, authorId: Long): PlaceComment?

    /** При слиянии мест у автора остаётся комментарий целевого места (один комментарий на пользователя и место). */
    @Modifying
    @Query(
        "delete from PlaceComment c where c.placeId = :source and c.authorId in " +
                "(select t.authorId from PlaceComment t where t.placeId = :target)"
    )
    fun deleteConflicting(@Param("source") source: Long, @Param("target") target: Long): Int

    @Modifying
    @Query("update PlaceComment c set c.placeId = :target where c.placeId = :source")
    fun moveToPlace(@Param("source") source: Long, @Param("target") target: Long): Int
}

@Repository
interface PlaceCommentReportRepository : JpaRepository<PlaceCommentReport, PlaceCommentReportId> {

    @Query("select count(r) from PlaceCommentReport r where r.id.commentId = :commentId")
    fun countByCommentId(@Param("commentId") commentId: Long): Long
}
