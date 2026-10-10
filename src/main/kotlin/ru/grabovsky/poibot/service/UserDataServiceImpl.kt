package ru.grabovsky.poibot.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.grabovsky.poibot.entity.PlaceRatingId
import ru.grabovsky.poibot.repository.FlowStateRepository
import ru.grabovsky.poibot.repository.PlaceCommentRepository
import ru.grabovsky.poibot.repository.PlaceRatingRepository
import ru.grabovsky.poibot.repository.PlaceRepository
import ru.grabovsky.poibot.repository.SavedPlaceRepository
import ru.grabovsky.poibot.repository.UserRepository
import ru.grabovsky.poibot.service.interfaces.UserDataService

@Service
class UserDataServiceImpl(
    private val userRepository: UserRepository,
    private val savedPlaceRepository: SavedPlaceRepository,
    private val placeRepository: PlaceRepository,
    private val ratingRepository: PlaceRatingRepository,
    private val commentRepository: PlaceCommentRepository,
    private val flowStateRepository: FlowStateRepository,
    private val objectMapper: ObjectMapper,
) : UserDataService {

    @Transactional(readOnly = true)
    override fun export(userId: Long): ByteArray {
        val user = userRepository.findUserByUserId(userId)
        val places = savedPlaceRepository.findByOwnerIdOrderByCreatedAtAscIdAsc(userId).map { place ->
            linkedMapOf(
                "name" to place.name,
                "address" to place.address,
                "description" to place.description,
                "website" to place.websiteUrl,
                "latitude" to place.lat,
                "longitude" to place.lon,
                "hasPhoto" to (place.photoFileId != null),
                "status" to place.status,
                "note" to place.note,
                "tags" to place.tags,
                "createdAt" to place.createdAt?.toString(),
                "myRating" to ratingRepository.findById(PlaceRatingId(place.placeId, userId)).orElse(null)?.value,
                "myComment" to commentRepository.findByPlaceIdAndAuthorId(place.placeId, userId)?.text,
            )
        }
        val data = linkedMapOf(
            "user" to linkedMapOf(
                "id" to userId,
                "username" to user?.userName,
                "firstName" to user?.firstName,
                "lastName" to user?.lastName,
                "createdAt" to user?.createdAt?.toString(),
            ),
            "places" to places,
        )
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data)
    }

    @Transactional
    override fun deleteAll(userId: Long) {
        val placeIds = savedPlaceRepository.findPlaceIdsByOwnerId(userId)
        flowStateRepository.deleteByUserId(userId)
        // Остальное (записи, публикации, токены, оценки, комментарии, жалобы, связи с чатами) удаляет БД каскадом
        userRepository.findUserByUserId(userId)?.let(userRepository::delete)
        userRepository.flush()
        if (placeIds.isNotEmpty()) {
            placeRepository.deleteUnused(placeIds)
        }
    }
}
