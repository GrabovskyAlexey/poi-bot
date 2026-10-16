package ru.grabovsky.poibot.service

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component

/**
 * Метрики бота для Prometheus (`/actuator/prometheus`). Все таймеры называются `poi.*` и получают тег `outcome`
 * (`ok` или `error`), поэтому долю ошибок и перцентили времени можно считать по любому из них.
 */
@Component
class BotMetrics(
    private val registry: MeterRegistry,
) {

    /** Выполняет [block] и записывает время в таймер [name] с тегами [tags] (пары «имя, значение»); исключение считается ошибкой и пробрасывается. */
    fun <T> timed(name: String, vararg tags: String, block: () -> T): T {
        val sample = Timer.start(registry)
        var outcome = OK
        try {
            return block()
        } catch (error: Throwable) {
            outcome = ERROR
            throw error
        } finally {
            sample.stop(Timer.builder(name).tags(*tags, OUTCOME, outcome).register(registry))
        }
    }

    /** Обработка апдейта Telegram: [type] - `callback`, `message`, `member`, `inline` или `other`. */
    fun <T> update(type: String, block: () -> T): T = timed(UPDATE, "type", type, block = block)

    /** Работа flow: [event] - `start`, `callback` или `message`. */
    fun <T> flow(flow: String, event: String, block: () -> T): T = timed(FLOW, "flow", flow, "event", event, block = block)

    companion object {
        const val UPDATE = "poi.update"
        const val FLOW = "poi.flow"
        private const val OUTCOME = "outcome"
        private const val OK = "ok"
        private const val ERROR = "error"
    }
}
