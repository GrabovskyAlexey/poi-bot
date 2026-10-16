package ru.grabovsky.poibot.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

class BotMetricsTest : ShouldSpec({
    val registry = SimpleMeterRegistry()
    val metrics = BotMetrics(registry)

    fun count(name: String, vararg tags: String) = registry.find(name).tags(*tags).timer()?.count() ?: 0L

    should("record a successful block with the ok outcome and return its result") {
        metrics.update("callback") { 42 } shouldBe 42

        count(BotMetrics.UPDATE, "type", "callback", "outcome", "ok") shouldBe 1L
    }

    should("record a failing block with the error outcome and rethrow the exception") {
        shouldThrow<IllegalStateException> { metrics.flow("ADD_PLACE", "callback") { error("boom") } }

        count(BotMetrics.FLOW, "flow", "ADD_PLACE", "event", "callback", "outcome", "error") shouldBe 1L
        count(BotMetrics.FLOW, "flow", "ADD_PLACE", "event", "callback", "outcome", "ok") shouldBe 0L
    }

    should("expose the timers to Prometheus with seconds and outcome series") {
        val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        BotMetrics(prometheus).update("message") { }

        prometheus.scrape() shouldContain "poi_update_seconds_count{outcome=\"ok\",type=\"message\"} 1"
    }
})
