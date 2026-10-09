package ru.grabovsky.poibot.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.grabovsky.poibot.geo.GeoUtils
import ru.grabovsky.poibot.geo.NameMatcher
import ru.grabovsky.poibot.repository.PlaceRepository
import ru.grabovsky.poibot.service.interfaces.PlaceCandidate
import ru.grabovsky.poibot.service.interfaces.PlaceMatchingService

@Service
class PlaceMatchingServiceImpl(
    private val placeRepository: PlaceRepository,
) : PlaceMatchingService {

    @Transactional(readOnly = true)
    override fun findCandidates(lat: Double, lon: Double, name: String): List<PlaceCandidate> {
        val box = GeoUtils.boundingBox(lat, lon, PlaceMatchingService.CANDIDATE_RADIUS_METERS.toDouble())
        return placeRepository.findInBox(box.minLat, box.maxLat, box.minLon, box.maxLon)
            .mapNotNull { place ->
                val placeLat = place.lat ?: return@mapNotNull null
                val placeLon = place.lon ?: return@mapNotNull null
                val distance = GeoUtils.distanceMeters(lat, lon, placeLat, placeLon).toInt()
                if (distance > PlaceMatchingService.CANDIDATE_RADIUS_METERS) return@mapNotNull null
                PlaceCandidate(
                    placeId = place.id!!,
                    name = place.displayName,
                    address = place.address,
                    distanceMeters = distance,
                    similarity = NameMatcher.similarity(name, place.displayName),
                )
            }
            .sortedWith(compareByDescending<PlaceCandidate> { it.similarity }.thenBy { it.distanceMeters })
            .take(PlaceMatchingService.MAX_CANDIDATES)
    }
}
