package ru.grabovsky.poibot.framework

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.info.BuildProperties
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** Пишет в лог версию приложения после старта: по ней видно, какая сборка реально развёрнута. */
@Component
class StartupInfo(
    private val buildProperties: ObjectProvider<BuildProperties>,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun logVersion() {
        val version = buildProperties.ifAvailable?.version ?: UNKNOWN_VERSION
        logger.info { "poibot version $version started" }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
        const val UNKNOWN_VERSION = "unknown"
    }
}
