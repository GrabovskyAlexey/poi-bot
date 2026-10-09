package ru.grabovsky.poibot.geo

import kotlin.math.max
import kotlin.math.min

/** Сравнение названий заведений: нормализация + расстояние Левенштейна. */
object NameMatcher {
    private val NOISE_WORDS = setOf(
        "бар", "кафе", "ресторан", "паб", "пекарня", "кофейня", "столовая", "пиццерия",
        "bar", "cafe", "restaurant", "pub", "bakery", "coffee", "pizzeria", "the",
    )
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}\\s]")
    private val SPACES = Regex("\\s+")
    private const val MIN_CONTAINMENT_LENGTH = 4
    private const val CONTAINMENT_SCORE = 0.8

    fun normalize(name: String): String {
        val words = name.lowercase()
            .replace('ё', 'е')
            .replace(NON_ALNUM, " ")
            .split(SPACES)
            .filter { it.isNotBlank() }
        val meaningful = words.filter { it !in NOISE_WORDS }
        return meaningful.ifEmpty { words }.joinToString(" ")
    }

    /** Схожесть 0..1 (1 — названия совпадают после нормализации). */
    fun similarity(first: String, second: String): Double {
        val a = normalize(first)
        val b = normalize(second)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val byDistance = 1.0 - levenshtein(a, b).toDouble() / max(a.length, b.length)
        val containment = if (min(a.length, b.length) >= MIN_CONTAINMENT_LENGTH && (a.contains(b) || b.contains(a))) {
            CONTAINMENT_SCORE
        } else {
            0.0
        }
        return max(byDistance, containment)
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
