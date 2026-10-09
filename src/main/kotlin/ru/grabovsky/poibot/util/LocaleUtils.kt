package ru.grabovsky.poibot.util

import ru.grabovsky.poibot.entity.User
import java.util.*

object LocaleUtils {
    val defaultLocale: Locale = Locale.forLanguageTag("ru")

    fun resolve(user: User?): Locale {
        return resolve(user?.profile?.locale ?: user?.language)
    }

    private fun resolve(language: String?): Locale {
        if (language.isNullOrBlank()) {
            return defaultLocale
        }
        val normalized = language.replace('_', '-').lowercase()
        // try as full language tag first
        val locale = runCatching { Locale.forLanguageTag(normalized) }.getOrNull()
        if (locale != null && locale.language.isNotBlank()) {
            return when (locale.language) {
                "ru" -> Locale.forLanguageTag("ru")
                else -> Locale.forLanguageTag(locale.language)
            }
        }
        return when {
            normalized.startsWith("ru") -> Locale.forLanguageTag("ru")
            else -> Locale.forLanguageTag(normalized.take(2))
        }
    }
}
