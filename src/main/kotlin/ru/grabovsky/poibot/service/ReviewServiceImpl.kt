package ru.grabovsky.poibot.service

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.grabovsky.poibot.entity.PlaceComment
import ru.grabovsky.poibot.entity.PlaceCommentReport
import ru.grabovsky.poibot.entity.PlaceCommentReportId
import ru.grabovsky.poibot.entity.PlaceRating
import ru.grabovsky.poibot.entity.PlaceRatingId
import ru.grabovsky.poibot.repository.PlaceCommentReportRepository
import ru.grabovsky.poibot.repository.PlaceCommentRepository
import ru.grabovsky.poibot.repository.PlaceRatingRepository
import ru.grabovsky.poibot.repository.SavedPlaceRepository
import ru.grabovsky.poibot.service.interfaces.*

@Service
class ReviewServiceImpl(
    private val ratingRepository: PlaceRatingRepository,
    private val commentRepository: PlaceCommentRepository,
    private val reportRepository: PlaceCommentReportRepository,
    private val savedPlaceRepository: SavedPlaceRepository,
) : ReviewService {

    @Transactional(readOnly = true)
    override fun summary(placeId: Long): RatingSummary {
        val aggregate = ratingRepository.aggregate(placeId)
        return if (aggregate.total == 0L) RatingSummary.EMPTY else RatingSummary(aggregate.average, aggregate.total.toInt())
    }

    @Transactional(readOnly = true)
    override fun userRating(placeId: Long, userId: Long): Int? =
        ratingRepository.findById(PlaceRatingId(placeId, userId)).orElse(null)?.value

    @Transactional
    override fun rate(userId: Long, savedPlaceId: Long, value: Int): RateResult {
        require(value in MIN_RATING..MAX_RATING) { "Rating must be between $MIN_RATING and $MAX_RATING" }
        val placeId = placeIdOf(userId, savedPlaceId) ?: return RateResult.NOT_OWNER
        val id = PlaceRatingId(placeId, userId)
        val existing = ratingRepository.findById(id).orElse(null)
        if (existing == null) {
            ratingRepository.save(PlaceRating(id, value))
        } else {
            existing.value = value
            ratingRepository.save(existing)
        }
        return RateResult.OK
    }

    @Transactional
    override fun removeRating(userId: Long, savedPlaceId: Long): Boolean {
        val placeId = placeIdOf(userId, savedPlaceId) ?: return false
        val id = PlaceRatingId(placeId, userId)
        if (!ratingRepository.existsById(id)) return false
        ratingRepository.deleteById(id)
        return true
    }

    @Transactional(readOnly = true)
    override fun commentCount(placeId: Long): Int =
        commentRepository.countByPlaceIdAndHiddenFalse(placeId).toInt()

    @Transactional(readOnly = true)
    override fun comments(placeId: Long, viewerId: Long, page: Int, pageSize: Int): CommentsPage {
        var result = commentRepository.findByPlaceIdAndHiddenFalseOrderByCreatedAtDescIdDesc(
            placeId, PageRequest.of(page.coerceAtLeast(0), pageSize),
        )
        val totalPages = result.totalPages.coerceAtLeast(1)
        if (page >= totalPages) {
            result = commentRepository.findByPlaceIdAndHiddenFalseOrderByCreatedAtDescIdDesc(
                placeId, PageRequest.of(totalPages - 1, pageSize),
            )
        }
        val items = result.content.map { CommentItem(it.id!!, it.text, it.createdAt, mine = it.authorId == viewerId) }
        return CommentsPage(items, result.number, totalPages, result.totalElements)
    }

    @Transactional(readOnly = true)
    override fun userComment(placeId: Long, userId: Long): CommentItem? =
        commentRepository.findByPlaceIdAndAuthorId(placeId, userId)
            ?.takeIf { !it.hidden }
            ?.let { CommentItem(it.id!!, it.text, it.createdAt, mine = true) }

    @Transactional
    override fun saveComment(userId: Long, savedPlaceId: Long, text: String): AddCommentResult {
        val placeId = placeIdOf(userId, savedPlaceId) ?: return AddCommentResult.NOT_OWNER
        val trimmed = text.trim()
        if (trimmed.length < ReviewService.MIN_COMMENT_LENGTH) return AddCommentResult.TOO_SHORT
        if (trimmed.length > ReviewService.MAX_COMMENT_LENGTH) return AddCommentResult.TOO_LONG
        val existing = commentRepository.findByPlaceIdAndAuthorId(placeId, userId)
        if (existing == null) {
            commentRepository.save(PlaceComment(placeId = placeId, authorId = userId, text = trimmed))
            return AddCommentResult.ADDED
        }
        if (existing.hidden) return AddCommentResult.HIDDEN
        existing.text = trimmed
        commentRepository.save(existing)
        return AddCommentResult.UPDATED
    }

    @Transactional
    override fun deleteComment(userId: Long, commentId: Long): Boolean {
        val comment = commentRepository.findById(commentId).orElse(null) ?: return false
        if (comment.authorId != userId) return false
        commentRepository.delete(comment)
        return true
    }

    @Transactional
    override fun report(userId: Long, commentId: Long): ReportResult {
        val comment = commentRepository.findById(commentId).orElse(null)
        if (comment == null || comment.hidden) return ReportResult.NOT_FOUND
        if (comment.authorId == userId) return ReportResult.OWN_COMMENT
        val id = PlaceCommentReportId(commentId, userId)
        if (reportRepository.existsById(id)) return ReportResult.ALREADY_REPORTED
        reportRepository.saveAndFlush(PlaceCommentReport(id))
        if (reportRepository.countByCommentId(commentId) >= ReviewService.REPORTS_TO_HIDE) {
            comment.hidden = true
            commentRepository.save(comment)
        }
        return ReportResult.REPORTED
    }

    private fun placeIdOf(userId: Long, savedPlaceId: Long): Long? =
        savedPlaceRepository.findByIdAndOwnerId(savedPlaceId, userId)?.placeId

    private companion object {
        const val MIN_RATING = 1
        const val MAX_RATING = 5
    }
}
