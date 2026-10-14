package ru.grabovsky.poibot.geo

import ru.grabovsky.poibot.entity.SavedPlace

/**
 * Фильтр `/nearby` по личным полям записи: статусу и тегу. Места групп чужие, у них нет ваших статусов и тегов,
 * поэтому при включённом фильтре они не показываются.
 */
data class NearbyFilter(val status: String? = null, val tag: String? = null) {
    val active: Boolean get() = status != null || tag != null

    fun matches(place: SavedPlace): Boolean =
        (status == null || place.status == status) && (tag == null || tag in place.tags)

    companion object {
        val NONE = NearbyFilter()
    }
}
