package ru.grabovsky.poibot.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.MapLinkHttp
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class MapLinkHttpImpl(private val objectMapper: ObjectMapper) : MapLinkHttp {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .build()

    override fun redirectTarget(url: String): String? =
        execute(url, BROWSER_UA, HttpResponse.BodyHandlers.discarding())
            ?.takeIf { it.statusCode() in 300..399 }
            ?.headers()?.firstValue("location")?.orElse(null)
            ?.let { runCatching { URI(url).resolve(it).toString() }.getOrNull() }

    override fun page(url: String): String? =
        execute(url, BROWSER_UA, HttpResponse.BodyHandlers.ofString())
            ?.takeIf { it.statusCode() == 200 }
            ?.body()?.take(MAX_PAGE_CHARS)

    override fun reverseAddress(lat: Double, lon: Double): String? {
        val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&accept-language=ru&lat=$lat&lon=$lon"
        val body = execute(url, NOMINATIM_UA, HttpResponse.BodyHandlers.ofString())
            ?.takeIf { it.statusCode() == 200 }?.body() ?: return null
        val address = runCatching { objectMapper.readTree(body).path("address") }.getOrNull() ?: return null
        fun part(vararg keys: String) =
            keys.firstNotNullOfOrNull { address.path(it).asText(null)?.takeIf(String::isNotBlank) }
        val street = listOfNotNull(part("road", "pedestrian", "footway"), part("house_number")).joinToString(", ")
        if (street.isBlank()) return null
        return listOfNotNull(street, part("city", "town", "village", "municipality")).joinToString(", ")
    }

    private fun <T> execute(url: String, userAgent: String, handler: HttpResponse.BodyHandler<T>): HttpResponse<T>? =
        runCatching {
            val request = HttpRequest.newBuilder(URI(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("User-Agent", userAgent)
                .header("Accept-Language", "ru")
                .GET().build()
            client.send(request, handler)
        }.onFailure { logger.warn { "HTTP request failed: ${it.message}" } }.getOrNull()

    private companion object {
        const val TIMEOUT_SECONDS = 5L
        const val MAX_PAGE_CHARS = 3_000_000
        const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"
        const val NOMINATIM_UA = "poibot/1.0 (+https://github.com/GrabovskyAlexey/poi-bot)"
        val logger = KotlinLogging.logger {}
    }
}
