package ru.grabovsky.poibot.service

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.grabovsky.poibot.repository.PlaceCommentReportRepository
import ru.grabovsky.poibot.repository.PlaceCommentRepository
import ru.grabovsky.poibot.repository.PlaceRepository
import ru.grabovsky.poibot.service.interfaces.HiddenComment
import ru.grabovsky.poibot.service.interfaces.ModerationService

@Service
class ModerationServiceImpl(
    private val comments: PlaceCommentRepository,
    private val reports: PlaceCommentReportRepository,
    private val places: PlaceRepository,
) : ModerationService {

    override fun hiddenCount(): Long = comments.countByHiddenTrue()

    @Transactional(readOnly = true)
    override fun hiddenAt(index: Int): HiddenComment? {
        if (index < 0) return null
        val comment = comments.findByHiddenTrueOrderByIdAsc(PageRequest.of(index, 1)).content.firstOrNull() ?: return null
        val place = places.findById(comment.placeId).orElse(null)
        return HiddenComment(
            id = comment.id!!,
            text = comment.text,
            placeName = place?.displayName.orEmpty(),
            placeAddress = place?.address,
            reports = reports.countByCommentId(comment.id!!),
        )
    }

    @Transactional
    override fun restore(commentId: Long): Boolean {
        val comment = comments.findById(commentId).orElse(null)?.takeIf { it.hidden } ?: return false
        reports.deleteByCommentId(commentId)
        comment.hidden = false
        comments.save(comment)
        return true
    }

    @Transactional
    override fun delete(commentId: Long): Boolean {
        val comment = comments.findById(commentId).orElse(null)?.takeIf { it.hidden } ?: return false
        comments.delete(comment)
        return true
    }
}
