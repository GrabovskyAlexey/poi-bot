package ru.grabovsky.poibot.service.interfaces

data class PlaceCandidate(
    val placeId: Long,
    val name: String,
    val address: String?,
    val distanceMeters: Int,
    val similarity: Double,
)

interface PlaceMatchingService {
    /** Существующие места рядом с точкой, лучшие совпадения по названию первыми. */
    fun findCandidates(lat: Double, lon: Double, name: String): List<PlaceCandidate>

    companion object {
        const val CANDIDATE_RADIUS_METERS = 75
        const val MAX_CANDIDATES = 5

        /** Порог схожести названия, начиная с которого вопрос «то же место?» задаётся одним тапом. */
        const val STRONG_SIMILARITY = 0.75
    }
}
