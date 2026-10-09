package ru.grabovsky.poibot.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    override fun searchOwn(ownerId: Long, lat: Double, lon: Double, selected: SearchRadius): NearbyResult {
        val maxMeters = SearchRadius.MAX.meters
        val box = GeoUtils.boundingBox(lat, lon, maxMeters.toDouble())
        val points = savedPlaceRepository.findOwnInBox(ownerId, box.minLat, box.maxLat, box.minLon, box.maxLon)
            .mapNotNull { place ->
                val placeLat = place.lat ?: return@mapNotNull null
                val placeLon = place.lon ?: return@mapNotNull null
                val distance = GeoUtils.distanceMeters(lat, lon, placeLat, placeLon).toInt()
                NearbyPoint(place, distance).takeIf { distance <= maxMeters }
            }
            .sortedBy { it.distanceMeters }
        return NearbyResult.build(points, selected)
    }
}
