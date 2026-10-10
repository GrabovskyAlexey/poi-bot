package ru.grabovsky.poibot.service.interfaces

import java.time.Instant

data class RatingSummary(val average: Double?, val count: Int) {
    companion object {
        val EMPTY = RatingSummary(null, 0)
    }
}

data class CommentItem(val id: Long, val text: String, val createdAt: Instant?, val mine: Boolean)

data class CommentsPage(val items: List<CommentItem>, val page: Int, val totalPages: Int, val total: Long)

enum class RateResult { OK, NOT_OWNER }

enum class AddCommentResult { ADDED, UPDATED, NOT_OWNER, TOO_SHORT, TOO_LONG, HIDDEN }

enum class ReportResult { REPORTED, ALREADY_REPORTED, OWN_COMMENT, NOT_FOUND }

/**
 * Рейтинг и комментарии привязаны к месту ([ru.grabovsky.poibot.entity.Place]) и общие для всех, кто его сохранил.
 * Оценивать и комментировать может только тот, у кого есть запись об этом месте; читать — все.
 */
interface ReviewService {
    fun summary(placeId: Long): RatingSummary

    /** Рейтинги нескольких мест одним запросом; места без оценок в результат не попадают. */
    fun summaries(placeIds: Collection<Long>): Map<Long, RatingSummary>

    fun userRating(placeId: Long, userId: Long): Int?

    /** Ставит или меняет оценку (1..5) места, на которое ссылается запись [savedPlaceId] пользователя. */
    fun rate(userId: Long, savedPlaceId: Long, value: Int): RateResult

    /** Убирает оценку пользователя; true, если она была. */
    fun removeRating(userId: Long, savedPlaceId: Long): Boolean

    fun commentCount(placeId: Long): Int

    fun comments(placeId: Long, viewerId: Long, page: Int, pageSize: Int): CommentsPage

    /** Свой комментарий пользователя к месту (даже если ещё не показан), нужен для редактирования. */
    fun userComment(placeId: Long, userId: Long): CommentItem?

    /**
     * Сохраняет комментарий: у пользователя один комментарий на место, повторный вызов заменяет текст.
     * Скрытый по жалобам комментарий изменить нельзя ([AddCommentResult.HIDDEN]).
     */
    fun saveComment(userId: Long, savedPlaceId: Long, text: String): AddCommentResult

    /** Автор удаляет свой комментарий. */
    fun deleteComment(userId: Long, commentId: Long): Boolean

    /** Жалоба на чужой комментарий; при [REPORTS_TO_HIDE] жалобах комментарий скрывается. */
    fun report(userId: Long, commentId: Long): ReportResult

    companion object {
        const val MIN_COMMENT_LENGTH = 2
        const val MAX_COMMENT_LENGTH = 500
        const val REPORTS_TO_HIDE = 3
    }
}
