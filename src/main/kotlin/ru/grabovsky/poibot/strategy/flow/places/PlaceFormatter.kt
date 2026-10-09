package ru.grabovsky.poibot.strategy.flow.places

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.I18nService
import ru.grabovsky.poibot.service.interfaces.RatingSummary
import java.util.*

/** Форматирование данных места для сообщений (расстояния, обрезка текста). */
@Component
class PlaceFormatter(
    private val i18n: I18nService,
) {
    fun distance(meters: Int, locale: Locale): String =
        if (meters < METERS_IN_KM) {
            "$meters ${i18n.i18n("unit.m", locale)}"
        } else {
            val km = String.format(Locale.ROOT, "%.1f", meters / METERS_IN_KM.toDouble()).removeSuffix(".0")
            "$km ${i18n.i18n("unit.km", locale)}"
        }

    /** «4.3 (12)» или null, если оценок нет. */
    fun rating(summary: RatingSummary): String? {
        val average = summary.average ?: return null
        if (summary.count == 0) return null
        return String.format(Locale.ROOT, "%.1f (%d)", average, summary.count)
    }

    fun shorten(text: String?, max: Int): String? =
        text?.takeIf { it.isNotBlank() }?.let { if (it.length > max) it.take(max - 1) + "…" else it }

    private companion object {
        const val METERS_IN_KM = 1000
    }
}
