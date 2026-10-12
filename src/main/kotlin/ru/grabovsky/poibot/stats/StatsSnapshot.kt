package ru.grabovsky.poibot.stats

import java.time.LocalDate
import java.time.ZoneId

/** Часовой пояс, по которому считаются дни и недели статистики (как и время в ссылках-приглашениях). */
val STATS_ZONE: ZoneId = ZoneId.of("Europe/Moscow")

/** Пользователи: всего, новые и активные за сегодня / 7 / 30 дней (включая сегодняшний). */
data class AudienceStats(
    val total: Long,
    val withPlaces: Long,
    val new1: Long,
    val new7: Long,
    val new30: Long,
    val active1: Long,
    val active7: Long,
    val active30: Long,
)

data class ContentStats(
    val savedPlaces: Long,
    val uniquePlaces: Long,
    val groups: Long,
    val publications: Long,
    val ratings: Long,
    val comments: Long,
    val hiddenComments: Long,
    val activeLinks: Long,
)

/** Неделя с понедельника [weekStart]; последняя в списке - текущая, ещё не закончившаяся. */
data class WeekStats(
    val weekStart: LocalDate,
    val newUsers: Long,
    val activeUsers: Long,
    val newPlaces: Long,
)

data class StatsSnapshot(
    val audience: AudienceStats,
    val content: ContentStats,
    val weeks: List<WeekStats>,
)
