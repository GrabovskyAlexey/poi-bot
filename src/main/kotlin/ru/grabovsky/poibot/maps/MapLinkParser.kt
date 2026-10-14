package ru.grabovsky.poibot.maps

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Сервисы карт, ссылки на которые умеем разбирать. */
enum class MapProvider { GOOGLE, YANDEX, TWOGIS }

/** Разбор ссылок Google Maps, Яндекс Карт и 2GIS: только чистые функции, без сети. */
object MapLinkParser {
    private val URL = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val GOOGLE_HOST = Regex("""(www\.|maps\.)?google\.[a-z.]{2,6}""")
    private val YANDEX_HOST = Regex("""(www\.)?yandex\.[a-z]{2,3}""")
    private val TWOGIS_HOST = Regex("""(www\.)?2gis\.[a-z]{2,3}""")
    private val GOOGLE_PLACE = Regex("""/maps/place/([^/@?]+)""")
    private val GOOGLE_PIN = Regex("""!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)""")
    private val GOOGLE_VIEW = Regex("""@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
    private val GOOGLE_QUERY = Regex("""[?&](?:q|query|ll)=(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)(?:&|$)""")
    private val TWOGIS_CENTER = Regex("""[?&]m=(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
    private val GOOGLE_FTID = Regex("""(?:!1s|ftid=)(0x[0-9a-f]+(?::|%3A)0x[0-9a-f]+)""", RegexOption.IGNORE_CASE)
    private val YANDEX_OID = Regex("""oid=(\d+)""")
    private val TWOGIS_FIRM = Regex("""/firm/(\d+)""")
    private val TWOGIS_GEO = Regex("""/geo/\d+/(-?\d+(?:\.\d+)?)(?:,|%2C)(-?\d+(?:\.\d+)?)""")
    private val YANDEX_SHORT_TITLE = Regex(""""shortTitle":"((?:[^"\\]|\\.)*)"""")
    private val YANDEX_FULL_ADDRESS = Regex(""""fullAddress":"((?:[^"\\]|\\.)*)"""")
    private val HTML_TITLE = Regex("""<title>([^<]*)</title>""")
    private val COORDS_ONLY = Regex("""-?\d+(\.\d+)?[, ]+-?\d+(\.\d+)?""")
    private val json = ObjectMapper()

    /** Первая ссылка из текста (сообщение целиком может быть и просто ссылкой без схемы). */
    fun findUrl(text: String): String? {
        URL.find(text)?.let { return it.value.trimEnd('.', ',', ')') }
        val bare = text.trim()
        return if (bare.isNotEmpty() && bare.none { it.isWhitespace() }) "https://$bare" else null
    }

    fun provider(url: String): MapProvider? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val path = uri.rawPath.orEmpty()
        return when {
            host == "maps.app.goo.gl" -> MapProvider.GOOGLE
            host == "goo.gl" && path.startsWith("/maps") -> MapProvider.GOOGLE
            GOOGLE_HOST.matches(host) && path.startsWith("/maps") -> MapProvider.GOOGLE
            YANDEX_HOST.matches(host) && path.startsWith("/maps") -> MapProvider.YANDEX
            host == "go.2gis.com" -> MapProvider.TWOGIS
            TWOGIS_HOST.matches(host) -> MapProvider.TWOGIS
            else -> null
        }
    }

    /** Ссылка-«сокращалка»: данных в ней нет, нужно пройти редирект. */
    fun isShort(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return host == "maps.app.goo.gl" || host == "goo.gl" || host == "go.2gis.com" ||
                (provider(url) == MapProvider.YANDEX && uri.rawPath.orEmpty().startsWith("/maps/-/"))
    }

    fun parseGoogle(url: String): MapImport? {
        val point = listOf(GOOGLE_PIN, GOOGLE_QUERY, GOOGLE_VIEW).firstNotNullOfOrNull { regex ->
            regex.find(url)?.let { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
        } ?: return null
        val name = GOOGLE_PLACE.find(url)?.groupValues?.get(1)?.let { decode(it.replace('+', ' ')) }?.trim()
            ?.takeIf { it.isNotEmpty() && !COORDS_ONLY.matches(it) }
        val ftid = GOOGLE_FTID.find(url)?.groupValues?.get(1)?.replace("%3A", ":", ignoreCase = true)?.lowercase()
        return validated(MapImport(name, null, point.first, point.second, ftid?.let { "google:$it" }))
    }

    /** Координаты организации (`poi[point]`) или точки (`pt`); порядок в ссылках Яндекса - долгота, широта. */
    fun parseYandex(url: String): MapImport? {
        val query = queryParams(url)
        val parts = (query["poi[point]"] ?: query["pt"] ?: return null).split(',')
        if (parts.size < 2) return null
        val lon = parts[0].toDoubleOrNull() ?: return null
        val lat = parts[1].toDoubleOrNull() ?: return null
        val oid = query["poi[uri]"]?.let { YANDEX_OID.find(it)?.groupValues?.get(1) }
        return validated(MapImport(null, null, lat, lon, oid?.let { "yandex:$it" }))
    }

    /** Добавляет название и адрес со страницы организации Яндекса; берём их, только если они совпадают с заголовком страницы. */
    fun enrichYandex(base: MapImport, html: String): MapImport {
        val title = HTML_TITLE.find(html)?.groupValues?.get(1)?.let(::decodeEntities)?.trim() ?: return base
        val name = YANDEX_SHORT_TITLE.find(html)?.groupValues?.get(1)?.let(::unescapeJson)?.trim()
            ?.takeIf { it.isNotEmpty() && title.startsWith(it) } ?: return base
        val address = YANDEX_FULL_ADDRESS.find(html)?.groupValues?.get(1)?.let(::unescapeJson)?.trim()
            ?.takeIf { it.isNotEmpty() }
        return base.copy(name = name, address = address)
    }

    /** У 2GIS в ссылке нет ни названия, ни точного адреса: только центр карты (`m=долгота,широта/масштаб`). */
    fun parseTwoGis(url: String): MapImport? {
        val match = TWOGIS_CENTER.find(url) ?: TWOGIS_GEO.find(url) ?: return null
        val firm = TWOGIS_FIRM.find(url)?.groupValues?.get(1)
        return validated(
            MapImport(null, null, match.groupValues[2].toDouble(), match.groupValues[1].toDouble(), firm?.let { "2gis:$it" })
        )
    }

    private fun validated(result: MapImport): MapImport? =
        result.takeIf { it.lat in -90.0..90.0 && it.lon in -180.0..180.0 }

    private fun queryParams(url: String): Map<String, String> =
        url.substringAfter('?', "").substringBefore('#').split('&').filter { '=' in it }.associate {
            decode(it.substringBefore('=')) to decode(it.substringAfter('='))
        }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8) }.getOrDefault(value)

    private fun unescapeJson(value: String): String =
        runCatching { json.readValue("\"$value\"", String::class.java) }.getOrDefault(value)

    private fun decodeEntities(value: String): String =
        value.replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
