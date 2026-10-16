package ru.grabovsky.poibot.service

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import ru.grabovsky.poibot.service.interfaces.StatsService
import ru.grabovsky.poibot.stats.AudienceStats
import ru.grabovsky.poibot.stats.ContentStats
import ru.grabovsky.poibot.stats.StatsSnapshot

class AudienceMetricsTest : ShouldSpec({
    val snapshot = StatsSnapshot(
        AudienceStats(total = 10, withPlaces = 6, new1 = 1, new7 = 2, new30 = 5, active1 = 3, active7 = 7, active30 = 9),
        ContentStats(savedPlaces = 40, uniquePlaces = 30, groups = 2, publications = 5, ratings = 8, comments = 4,
            hiddenComments = 0, activeLinks = 1),
        emptyList(),
    )

    should("expose audience and content gauges from one cached snapshot") {
        val stats = mockk<StatsService> { every { snapshot(any()) } returns snapshot }
        val registry = SimpleMeterRegistry()
        AudienceMetrics(stats).bindTo(registry)

        registry.get("poi.users.total").gauge().value() shouldBe 10.0
        registry.get("poi.users.active").tag("period", "day").gauge().value() shouldBe 3.0
        registry.get("poi.users.active").tag("period", "week").gauge().value() shouldBe 7.0
        registry.get("poi.places.saved").gauge().value() shouldBe 40.0
        registry.get("poi.places.unique").gauge().value() shouldBe 30.0
        verify(exactly = 1) { stats.snapshot(any()) }
    }

    should("report NaN instead of failing when statistics are unavailable") {
        val stats = mockk<StatsService> { every { snapshot(any()) } throws IllegalStateException("db down") }
        val registry = SimpleMeterRegistry()
        AudienceMetrics(stats).bindTo(registry)

        registry.get("poi.users.total").gauge().value().isNaN() shouldBe true
    }
})
