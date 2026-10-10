package ru.grabovsky.poibot.service

import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.service.interfaces.PlaceListEntry
import ru.grabovsky.poibot.service.interfaces.PlaceListQuery
import ru.grabovsky.poibot.service.interfaces.PlaceSort
import ru.grabovsky.poibot.service.interfaces.RatingSummary
import java.util.*

/** Чистые функции списков мест: поиск, фильтры, склейка дублей и сортировка. */
object PlaceListing {

    fun matches(place: SavedPlace, query: PlaceListQuery): Boolean {
        if (query.withLocation && !place.hasLocation()) return false
        if (query.withPhoto && place.photoFileId == null) return false
        if (query.tag != null && query.tag !in place.tags) return false
        if (query.status != null && place.status != query.status) return false
        val text = query.text?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return true
        return place.name.lowercase(Locale.ROOT).contains(text) ||
                place.address?.lowercase(Locale.ROOT)?.contains(text) == true ||
                place.note?.lowercase(Locale.ROOT)?.contains(text) == true ||
                place.tags.any { it.contains(text) }
    }

    /** Теги мест от самых частых к редким (при равенстве - по алфавиту). */
    fun popularTags(places: List<SavedPlace>): List<String> =
        places.flatMap { it.tags }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

    /** Одно заведение (`place_id`) показывается один раз: остаётся первая запись в порядке списка. */
    fun distinctByPlace(places: List<SavedPlace>): List<SavedPlace> = places.distinctBy { it.placeId }

    /** Сортировка устойчивая: равные по ключу записи остаются в исходном порядке (новые сверху). */
    fun sort(places: List<SavedPlace>, sort: PlaceSort, ratings: Map<Long, RatingSummary>): List<SavedPlace> =
        when (sort) {
            PlaceSort.NEW -> places
            PlaceSort.NAME -> places.sortedBy { it.name.lowercase(Locale.ROOT) }
            PlaceSort.RATING -> places.sortedWith(
                compareByDescending<SavedPlace> { ratings[it.placeId]?.average ?: -1.0 }
                    .thenByDescending { ratings[it.placeId]?.count ?: 0 }
            )
        }

    fun entries(places: List<SavedPlace>, ratings: Map<Long, RatingSummary>): List<PlaceListEntry> =
        places.map { PlaceListEntry(it, ratings[it.placeId] ?: RatingSummary.EMPTY) }
}
