package ru.grabovsky.poibot.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.geo.BoundingBox
import ru.grabovsky.poibot.geo.GeoUtils
import ru.grabovsky.poibot.geo.NearbyPoint
import ru.grabovsky.poibot.geo.NearbyResult
import ru.grabovsky.poibot.geo.SearchRadius
import ru.grabovsky.poibot.repository.SavedPlaceRepository
import ru.grabovsky.poibot.repository.UserChatRepository
import ru.grabovsky.poibot.service.interfaces.NearbySearchService

@Service
class NearbySearchServiceImpl(
    private val savedPlaceRepository: SavedPlaceRepository,
    private val userChatRepository: UserChatRepository,
) : NearbySearchService {

    @Transactional(readOnly = true)
    override fun searchOwn(
        ownerId: Long,
        lat: Double,
        lon: Double,
        selected: SearchRadius,
        includeGroups: Boolean,
    ): NearbyResult {
        val chatIds = if (includeGroups) userChatIds(ownerId) else emptyList()
        val ownPlaceIds = mutableSetOf<Long>()
        val own = points(lat, lon, false) { box ->
            savedPlaceRepository.findOwnInBox(ownerId, box.minLat, box.maxLat, box.minLon, box.maxLon)
        }.onEach { ownPlaceIds += it.place.placeId }
        val groups = if (chatIds.isEmpty()) {
            emptyList()
        } else {
            // Своё место из группы не дублируем, одно заведение из нескольких групп - ближайшая запись
            points(lat, lon, true) { box ->
                savedPlaceRepository.findPublishedInChatsBox(chatIds, box.minLat, box.maxLat, box.minLon, box.maxLon)
            }.filter { it.place.placeId !in ownPlaceIds }.distinctBy { it.place.placeId }
        }
        return NearbyResult.build((own + groups).sortedBy { it.distanceMeters }, selected)
    }

    @Transactional(readOnly = true)
    override fun findInUserGroups(userId: Long, savedPlaceId: Long): SavedPlace? {
        val chatIds = userChatIds(userId).ifEmpty { return null }
        return savedPlaceRepository.findPublishedInChatsById(chatIds, savedPlaceId)
    }

    private fun userChatIds(userId: Long): List<Long> = userChatRepository.findActiveChatsByUserId(userId).map { it.id }

    @Transactional(readOnly = true)
    override fun searchInChat(chatId: Long, lat: Double, lon: Double, selected: SearchRadius): NearbyResult =
        search(lat, lon, selected, distinctPlaces = true) { box ->
            savedPlaceRepository.findPublishedInBox(chatId, box.minLat, box.maxLat, box.minLon, box.maxLon)
        }

    private fun search(
        lat: Double,
        lon: Double,
        selected: SearchRadius,
        distinctPlaces: Boolean = false,
        load: (BoundingBox) -> List<SavedPlace>,
    ): NearbyResult {
        val points = points(lat, lon, false, load)
            // В группе одно заведение, опубликованное несколькими участниками, показываем один раз (ближайшая запись)
            .let { sorted -> if (distinctPlaces) sorted.distinctBy { it.place.placeId } else sorted }
        return NearbyResult.build(points, selected)
    }

    /** Записи с координатами в пределах максимального радиуса, по возрастанию расстояния. */
    private fun points(lat: Double, lon: Double, fromGroup: Boolean, load: (BoundingBox) -> List<SavedPlace>): List<NearbyPoint> {
        val maxMeters = SearchRadius.MAX.meters
        return load(GeoUtils.boundingBox(lat, lon, maxMeters.toDouble()))
            .mapNotNull { place ->
                val placeLat = place.lat ?: return@mapNotNull null
                val placeLon = place.lon ?: return@mapNotNull null
                val distance = GeoUtils.distanceMeters(lat, lon, placeLat, placeLon).toInt()
                NearbyPoint(place, distance, fromGroup).takeIf { distance <= maxMeters }
            }
            .sortedBy { it.distanceMeters }
    }
}
