package ru.grabovsky.poibot.geo

import ru.grabovsky.poibot.entity.SavedPlace

data class NearbyPoint(val place: SavedPlace, val distanceMeters: Int)

/** Сводка по большему радиусу: сколько точек добавляется к уже показанным. */
data class RadiusHint(val radius: SearchRadius, val extra: Int, val total: Int)

data class NearbyResult(
    val selected: SearchRadius,
    val points: List<NearbyPoint>,
    val hints: List<RadiusHint>,
) {
    companion object {
        /**
         * @param all все точки в пределах максимального радиуса, по возрастанию расстояния.
         * Подсказки строятся только для радиусов больше выбранного и только там, где точек реально прибавляется.
         */
        fun build(all: List<NearbyPoint>, selected: SearchRadius): NearbyResult {
            val shown = all.filter { it.distanceMeters <= selected.meters }
            var previousTotal = shown.size
            val hints = SearchRadius.entries
                .filter { it.meters > selected.meters }
                .mapNotNull { radius ->
                    val total = all.count { it.distanceMeters <= radius.meters }
                    if (total > previousTotal) {
                        RadiusHint(radius, total - previousTotal, total).also { previousTotal = total }
                    } else {
                        null
                    }
                }
            return NearbyResult(selected, shown, hints)
        }
    }
}
