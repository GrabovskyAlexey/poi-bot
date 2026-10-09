package ru.grabovsky.poibot.service

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.grabovsky.poibot.entity.Place
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.repository.PlaceRepository
import ru.grabovsky.poibot.repository.SavedPlaceRepository
import ru.grabovsky.poibot.service.interfaces.PlaceLimitExceededException
import ru.grabovsky.poibot.service.interfaces.SavedPlaceDraft
import ru.grabovsky.poibot.service.interfaces.SavedPlacePage
import ru.grabovsky.poibot.service.interfaces.SavedPlaceService

@Service
class SavedPlaceServiceImpl(
    private val savedPlaceRepository: SavedPlaceRepository,
    private val placeRepository: PlaceRepository,
) : SavedPlaceService {

    @Transactional
    override fun create(ownerId: Long, draft: SavedPlaceDraft, linkPlaceId: Long?): SavedPlace {
        if (savedPlaceRepository.countByOwnerId(ownerId) >= SavedPlaceService.MAX_PLACES_PER_USER) {
            throw PlaceLimitExceededException(SavedPlaceService.MAX_PLACES_PER_USER)
        }
        val placeId = resolveLinkedPlaceId(linkPlaceId) ?: newPlace(draft).id!!
        val entity = SavedPlace(ownerId = ownerId, placeId = placeId, name = draft.name)
        entity.apply(draft)
        return savedPlaceRepository.save(entity)
    }

    @Transactional
    override fun update(ownerId: Long, id: Long, draft: SavedPlaceDraft, linkPlaceId: Long?): SavedPlace? {
        val entity = savedPlaceRepository.findByIdAndOwnerId(id, ownerId) ?: return null
        val geoChanged = entity.lat != draft.lat || entity.lon != draft.lon
        entity.apply(draft)
        val linked = resolveLinkedPlaceId(linkPlaceId)
        when {
            linked != null -> entity.placeId = linked
            geoChanged -> entity.placeId = detachedPlaceFor(entity)
        }
        return savedPlaceRepository.save(entity)
    }

    @Transactional(readOnly = true)
    override fun get(ownerId: Long, id: Long): SavedPlace? =
        savedPlaceRepository.findByIdAndOwnerId(id, ownerId)

    @Transactional(readOnly = true)
    override fun list(ownerId: Long, page: Int, pageSize: Int): SavedPlacePage {
        val total = savedPlaceRepository.countByOwnerId(ownerId)
        val totalPages = ((total + pageSize - 1) / pageSize).toInt().coerceAtLeast(1)
        val safePage = page.coerceIn(0, totalPages - 1)
        val result = savedPlaceRepository.findByOwnerIdOrderByCreatedAtDescIdDesc(
            ownerId, PageRequest.of(safePage, pageSize)
        )
        return SavedPlacePage(result.content, safePage, totalPages, total)
    }

    @Transactional
    override fun delete(ownerId: Long, id: Long): Boolean {
        val entity = savedPlaceRepository.findByIdAndOwnerId(id, ownerId) ?: return false
        savedPlaceRepository.delete(entity)
        return true
    }

    private fun SavedPlace.apply(draft: SavedPlaceDraft) {
        name = draft.name
        address = draft.address
        description = draft.description
        websiteUrl = draft.websiteUrl
        photoFileId = draft.photoFileId
        photoFileUniqueId = draft.photoFileUniqueId
        lat = draft.lat
        lon = draft.lon
    }

    private fun resolveLinkedPlaceId(linkPlaceId: Long?): Long? {
        linkPlaceId ?: return null
        var place = placeRepository.findById(linkPlaceId).orElse(null) ?: return null
        // Место могли слить с другим: следуем по цепочке до актуального
        while (place.mergedIntoId != null) {
            place = placeRepository.findById(place.mergedIntoId!!).orElse(null) ?: return null
        }
        return place.id
    }

    private fun newPlace(draft: SavedPlaceDraft): Place = placeRepository.save(
        Place(lat = draft.lat, lon = draft.lon, displayName = draft.name, address = draft.address)
    )

    /**
     * Геопозиция записи изменилась без явной привязки к другому месту:
     * если место принадлежит только этой записи — обновляем его, иначе создаём новое.
     */
    private fun detachedPlaceFor(entity: SavedPlace): Long {
        val current = placeRepository.findById(entity.placeId).orElse(null)
        val shared = savedPlaceRepository.countByPlaceId(entity.placeId) > 1
        if (current != null && !shared) {
            current.lat = entity.lat
            current.lon = entity.lon
            current.displayName = entity.name
            current.address = entity.address
            return placeRepository.save(current).id!!
        }
        return placeRepository.save(
            Place(lat = entity.lat, lon = entity.lon, displayName = entity.name, address = entity.address)
        ).id!!
    }
}
