package ru.grabovsky.poibot.geo

enum class SearchRadius(val meters: Int) {
    M100(100),
    M250(250),
    M500(500),
    M1000(1000);

    companion object {
        val DEFAULT = M250
        val MAX = M1000

        fun fromMeters(meters: Int?): SearchRadius = entries.firstOrNull { it.meters == meters } ?: DEFAULT
    }
}
