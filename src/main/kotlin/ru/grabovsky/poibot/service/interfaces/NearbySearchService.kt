package ru.grabovsky.poibot.service.interfaces

import ru.grabovsky.poibot.geo.NearbyResult
import ru.grabovsky.poibot.geo.SearchRadius

interface NearbySearchService {
    /**
     * Ищет собственные точки пользователя всегда в максимальном радиусе и раскладывает результат
     * по выбранному радиусу (см. [NearbyResult]).
     */
    fun searchOwn(ownerId: Long, lat: Double, lon: Double, selected: SearchRadius): NearbyResult
}
