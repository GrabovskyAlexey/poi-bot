package ru.grabovsky.poibot.util

/** Разбор и нормализация пользовательских тегов. */
object TagUtils {
    const val MAX_TAGS = 5
    const val MAX_TAG_LENGTH = 30
    private val SEPARATORS = Regex("[,;\\s]+")

    /** Теги из присланного текста (через запятую, пробел или с новой строки; `#` отбрасывается); null - ввод некорректен. */
    fun parse(text: String): List<String>? {
        val tags = text.split(SEPARATORS)
            .map { it.trim().trimStart('#').lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (tags.isEmpty() || tags.size > MAX_TAGS || tags.any { it.length > MAX_TAG_LENGTH }) return null
        return tags
    }
}
