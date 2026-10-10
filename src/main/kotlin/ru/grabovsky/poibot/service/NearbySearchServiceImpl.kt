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
import ru.grabovsky.poibot.service.interfaces.NearbySearchService

@Service
class NearbySearchServiceImpl(
    private val savedPlaceRepository: SavedPlaceRepository,
) : NearbySearchService {

    @Transactional(readOnly = true)
    override fun searchOwn(ownerId: Long, lat: Double, lon: Double, selected: SearchRadius): NearbyResult =
        search(lat, lon, selected) { box ->
            savedPlaceRepository.findOwnInBox(ownerId, box.minLat, box.maxLat, box.minLon, box.maxLon)
        }

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
        val maxMeters = SearchRadius.MAX.meters
        val points = load(GeoUtils.boundingBox(lat, lon, maxMeters.toDouble()))
            .mapNotNull { place ->
                val placeLat = place.lat ?: return@mapNotNull null
                val placeLon = place.lon ?: return@mapNotNull null
                val distance = GeoUtils.distanceMeters(lat, lon, placeLat, placeLon).toInt()
                NearbyPoint(place, distance).takeIf { distance <= maxMeters }
            }
            .sortedBy { it.distanceMeters }
            // В группе одно заведение, опубликованное несколькими участниками, показываем один раз (ближайшая запись)
            .let { sorted -> if (distinctPlaces) sorted.distinctBy { it.place.placeId } else sorted }
        return NearbyResult.build(points, selected)
    }
}
