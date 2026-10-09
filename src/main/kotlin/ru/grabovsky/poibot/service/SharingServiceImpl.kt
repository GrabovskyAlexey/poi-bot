package ru.grabovsky.poibot.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.grabovsky.poibot.entity.PlaceShareToken
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.repository.PlaceShareTokenRepository
import ru.grabovsky.poibot.repository.SavedPlaceRepository
import ru.grabovsky.poibot.service.interfaces.*
import java.security.SecureRandom
import java.util.Base64

@Service
class SharingServiceImpl(
    private val tokenRepository: PlaceShareTokenRepository,
    private val savedPlaceRepository: SavedPlaceRepository,
    private val savedPlaceService: SavedPlaceService,
) : SharingService {

    private val random = SecureRandom()

    @Transactional
    override fun getOrCreateToken(ownerId: Long, savedPlaceId: Long): String? {
        savedPlaceRepository.findByIdAndOwnerId(savedPlaceId, ownerId) ?: return null
        tokenRepository.findBySavedPlaceId(savedPlaceId)?.let { return it.token }
        return tokenRepository.save(
            PlaceShareToken(token = newToken(), savedPlaceId = savedPlaceId, createdBy = ownerId)
        ).token
    }

    @Transactional(readOnly = true)
    override fun findShared(token: String): SavedPlace? {
        if (!TOKEN_FORMAT.matches(token)) return null
        val shared = tokenRepository.findById(token).orElse(null) ?: return null
        return savedPlaceRepository.findById(shared.savedPlaceId).orElse(null)
    }

    @Transactional
    override fun accept(userId: Long, token: String): AcceptResult {
        val source = findShared(token) ?: return AcceptResult.NotFound
        if (source.ownerId == userId) return AcceptResult.OwnPlace
        if (savedPlaceRepository.existsByOwnerIdAndPlaceId(userId, source.placeId)) return AcceptResult.AlreadySaved
        val draft = SavedPlaceDraft(
            name = source.name,
            address = source.address,
            description = source.description,
            websiteUrl = source.websiteUrl,
            photoFileId = source.photoFileId,
            photoFileUniqueId = source.photoFileUniqueId,
            lat = source.lat,
            lon = source.lon,
        )
        return try {
            AcceptResult.Saved(savedPlaceService.create(userId, draft, linkPlaceId = source.placeId))
        } catch (error: PlaceLimitExceededException) {
            AcceptResult.LimitReached
        }
    }

    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val TOKEN_BYTES = 16
        val TOKEN_FORMAT = Regex("^[A-Za-z0-9_-]{16,32}$")
    }
}
