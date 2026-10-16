package ru.grabovsky.poibot.service

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.StatsService
import ru.grabovsky.poibot.stats.StatsSnapshot
import java.time.Duration
import java.time.Instant

/**
 * Аудитория и контент бота как метрики. Снимок статистики (тот же, что видит `/admin`) читается из БД не чаще раза
 * в [CACHE_TTL], а не при каждом сборе метрик.
 */
@Component
class AudienceMetrics(
    private val stats: StatsService,
) : MeterBinder {

    @Volatile
    private var cached: Pair<Instant, StatsSnapshot>? = null

    override fun bindTo(registry: MeterRegistry) {
        gauge(registry, "poi.users.total", "Всего пользователей") { it.audience.total }
        gauge(registry, "poi.users.active", "Активных пользователей за период", "period" to "day") { it.audience.active1 }
        gauge(registry, "poi.users.active", "Активных пользователей за период", "period" to "week") { it.audience.active7 }
        gauge(registry, "poi.places.saved", "Сохранённых записей о местах") { it.content.savedPlaces }
        gauge(registry, "poi.places.unique", "Уникальных мест") { it.content.uniquePlaces }
    }

    private fun gauge(
        registry: MeterRegistry,
        name: String,
        description: String,
        vararg tags: Pair<String, String>,
        value: (StatsSnapshot) -> Long,
    ) {
        val builder = Gauge.builder(name) { snapshot()?.let(value)?.toDouble() ?: Double.NaN }.description(description)
        tags.forEach { (key, tagValue) -> builder.tag(key, tagValue) }
        builder.register(registry)
    }

    /** Снимок из кэша; при сбое БД отдаёт прошлый (или ничего), чтобы сбор метрик не падал. */
    private fun snapshot(): StatsSnapshot? {
        val now = Instant.now()
        cached?.takeIf { Duration.between(it.first, now) < CACHE_TTL }?.let { return it.second }
        return runCatching { stats.snapshot(now) }.getOrNull()
            ?.also { cached = now to it }
            ?: cached?.second
    }

    private companion object {
        val CACHE_TTL: Duration = Duration.ofMinutes(5)
    }
}
