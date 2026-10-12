package ru.grabovsky.poibot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import ru.grabovsky.poibot.maps.MapImport
import ru.grabovsky.poibot.maps.MapLinkParser
import ru.grabovsky.poibot.maps.MapProvider
import ru.grabovsky.poibot.service.interfaces.MapLinkHttp
import ru.grabovsky.poibot.service.interfaces.MapLinkImportService

/**
 * Достаёт из ссылки всё, что отдают сами карты без ключей API: у Google - название и координаты, у Яндекса - ещё и адрес
 * (со страницы организации), у 2GIS - только центр карты. Если адреса нет, берём его по координатам из OpenStreetMap.
 * Остальное пользователь вводит сам.
 */
@Service
class MapLinkImportServiceImpl(private val http: MapLinkHttp) : MapLinkImportService {

    override fun import(text: String): MapImport? {
        val start = MapLinkParser.findUrl(text)?.takeIf { MapLinkParser.provider(it) != null } ?: return null
        return runCatching { load(start) }
            .onFailure { logger.warn { "Map link import failed: ${it.message}" } }
            .getOrNull()
    }

    private fun load(start: String): MapImport? {
        val url = followRedirects(start) ?: return null
        val parsed = when (MapLinkParser.provider(url)) {
            MapProvider.GOOGLE -> MapLinkParser.parseGoogle(url)
            MapProvider.TWOGIS -> MapLinkParser.parseTwoGis(url)
            MapProvider.YANDEX -> MapLinkParser.parseYandex(url)?.let { base ->
                if (url.contains("poi[uri]") || url.contains("poi%5Buri%5D")) {
                    http.page(url)?.let { MapLinkParser.enrichYandex(base, it) } ?: base
                } else base
            }
            null -> null
        } ?: return null
        if (parsed.address != null) return parsed
        return parsed.copy(address = http.reverseAddress(parsed.lat, parsed.lon))
    }

    /** Идёт по редиректам коротких ссылок, не покидая сервисы карт. */
    private fun followRedirects(start: String): String? {
        var url = start
        repeat(MAX_REDIRECTS) {
            if (!MapLinkParser.isShort(url)) return url
            val next = http.redirectTarget(url) ?: return null
            if (MapLinkParser.provider(next) == null) return null
            url = next
        }
        return url.takeUnless { MapLinkParser.isShort(it) }
    }

    private companion object {
        const val MAX_REDIRECTS = 4
        val logger = KotlinLogging.logger {}
    }
}
