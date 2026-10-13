package ru.grabovsky.poibot.service.interfaces

import ru.grabovsky.poibot.geo.NearbyFilter
import ru.grabovsky.poibot.geo.NearbyResult
import ru.grabovsky.poibot.geo.SearchRadius

interface NearbySearchService {
    /**
     * Ищет собственные точки пользователя всегда в максимальном радиусе и раскладывает результат
     * по выбранному радиусу (см. [NearbyResult]). С [includeGroups] добавляет места, опубликованные в группах
     * пользователя (помечены [ru.grabovsky.poibot.geo.NearbyPoint.fromGroup]); место, которое уже есть у самого
     * пользователя, или опубликованное в нескольких группах, показывается один раз. Активный [filter] оставляет только
     * свои записи с нужным статусом и тегом: места групп при этом не показываются.
     */
    fun searchOwn(
        ownerId: Long,
        lat: Double,
        lon: Double,
        selected: SearchRadius,
        includeGroups: Boolean = false,
        filter: NearbyFilter = NearbyFilter.NONE,
    ): NearbyResult

    /** Запись из группы пользователя по id (для карточки в `/nearby`); null, если она не опубликована в его группах. */
    fun findInUserGroups(userId: Long, savedPlaceId: Long): ru.grabovsky.poibot.entity.SavedPlace?

    /** То же для записей, опубликованных в группе. */
    fun searchInChat(chatId: Long, lat: Double, lon: Double, selected: SearchRadius): NearbyResult
}
