package ru.grabovsky.poibot.service.interfaces

data class PlaceCandidate(
    val placeId: Long,
    val name: String,
    val address: String?,
    val distanceMeters: Int,
    val similarity: Double,
)

interface PlaceMatchingService {
    /**
     * Существующие места рядом с точкой, лучшие совпадения по названию первыми. Место с тем же [orgRef] (id организации
     * в сервисе карт) - это то же заведение: оно идёт первым с полным совпадением, где бы оно ни стояло на карте.
     */
    fun findCandidates(lat: Double, lon: Double, name: String, orgRef: String? = null): List<PlaceCandidate>

    companion object {
        const val CANDIDATE_RADIUS_METERS = 75
        const val MAX_CANDIDATES = 5

        /** Порог схожести названия, начиная с которого вопрос «то же место?» задаётся одним тапом. */
        const val STRONG_SIMILARITY = 0.75
    }
}
