package ru.grabovsky.poibot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.grabovsky.poibot.entity.PlaceRating
import ru.grabovsky.poibot.entity.PlaceRatingId
import ru.grabovsky.poibot.repository.PlaceCommentRepository
import ru.grabovsky.poibot.repository.PlaceRatingRepository
import ru.grabovsky.poibot.repository.PlaceRepository
import ru.grabovsky.poibot.repository.SavedPlaceRepository
import ru.grabovsky.poibot.service.interfaces.PlaceCandidate
import ru.grabovsky.poibot.service.interfaces.PlaceLinkService
import ru.grabovsky.poibot.service.interfaces.PlaceMatchingService
import ru.grabovsky.poibot.service.interfaces.RelinkResult

@Service
class PlaceLinkServiceImpl(
    private val savedPlaceRepository: SavedPlaceRepository,
    private val placeRepository: PlaceRepository,
    private val ratingRepository: PlaceRatingRepository,
    private val commentRepository: PlaceCommentRepository,
    private val placeMatchingService: PlaceMatchingService,
) : PlaceLinkService {

    @Transactional(readOnly = true)
    override fun candidatesFor(userId: Long, savedPlaceId: Long): List<PlaceCandidate> {
        val record = savedPlaceRepository.findByIdAndOwnerId(savedPlaceId, userId) ?: return emptyList()
        val lat = record.lat ?: return emptyList()
        val lon = record.lon ?: return emptyList()
        return placeMatchingService.findCandidates(lat, lon, record.name).filter { it.placeId != record.placeId }
    }

    @Transactional
    override fun relink(userId: Long, savedPlaceId: Long, newPlaceId: Long): RelinkResult {
        val record = savedPlaceRepository.findByIdAndOwnerId(savedPlaceId, userId) ?: return RelinkResult.NOT_FOUND
        val target = placeRepository.findById(newPlaceId).orElse(null)
        if (target == null || target.mergedIntoId != null) return RelinkResult.NOT_FOUND
        val oldPlaceId = record.placeId
        if (oldPlaceId == newPlaceId) return RelinkResult.SAME_PLACE

        record.placeId = newPlaceId
        savedPlaceRepository.saveAndFlush(record)
        moveRating(oldPlaceId, newPlaceId, userId)

        if (savedPlaceRepository.countByPlaceId(oldPlaceId) == 0L) {
            mergePlace(oldPlaceId, newPlaceId)
        }
        return RelinkResult.RELINKED
    }

    /** Оценка пользователя переезжает на новое место, если там у него ещё нет своей. */
    private fun moveRating(oldPlaceId: Long, newPlaceId: Long, userId: Long) {
        val oldId = PlaceRatingId(oldPlaceId, userId)
        val rating = ratingRepository.findById(oldId).orElse(null) ?: return
        ratingRepository.delete(rating)
        ratingRepository.flush()
        val newId = PlaceRatingId(newPlaceId, userId)
        if (!ratingRepository.existsById(newId)) {
            ratingRepository.save(PlaceRating(newId, rating.value))
        }
    }

    /** На старом месте записей не осталось: переносим оценки и комментарии и помечаем место слитым. */
    private fun mergePlace(oldPlaceId: Long, newPlaceId: Long) {
        val remaining = ratingRepository.findAllByPlaceId(oldPlaceId)
        remaining.forEach { rating ->
            ratingRepository.delete(rating)
            ratingRepository.flush()
            val newId = PlaceRatingId(newPlaceId, rating.id.userId)
            if (!ratingRepository.existsById(newId)) {
                ratingRepository.save(PlaceRating(newId, rating.value))
            }
        }
        commentRepository.deleteConflicting(oldPlaceId, newPlaceId)
        commentRepository.moveToPlace(oldPlaceId, newPlaceId)
        placeRepository.findById(oldPlaceId).ifPresent {
            it.mergedIntoId = newPlaceId
            placeRepository.save(it)
        }
        logger.info { "Place $oldPlaceId merged into $newPlaceId" }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
