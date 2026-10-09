package ru.grabovsky.poibot.util

import java.net.URI

object UrlUtils {
    const val MAX_LENGTH = 500
    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    /** Похоже ли сообщение на ссылку (одно «слово» с точкой или явная схема). */
    fun looksLikeUrl(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
        return SCHEME.containsMatchIn(trimmed) || (trimmed.contains('.') && !trimmed.endsWith("."))
    }

    /** Приводит ввод к виду https://host/path или возвращает null, если это не http(s)-ссылка. */
    fun normalize(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH || trimmed.any { it.isWhitespace() }) return null
        val withScheme = if (SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        val host = uri.host
        if ((scheme != "http" && scheme != "https") || host.isNullOrBlank() || !host.contains('.')) return null
        return withScheme
    }
}
