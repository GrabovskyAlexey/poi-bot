package ru.grabovsky.poibot.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import ru.grabovsky.poibot.stats.ContentStats
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

/** Агрегаты для статистики администратора; недели и дни считаются по МСК (см. `STATS_ZONE`). */
@Repository
class StatsRepository(private val jdbc: JdbcTemplate) {

    fun countUsers(): Long = count("SELECT COUNT(*) FROM poi_bot.users")

    fun countUsersWithPlaces(): Long = count("SELECT COUNT(DISTINCT owner_id) FROM poi_bot.saved_place")

    fun countUsersCreatedSince(since: Instant): Long =
        count("SELECT COUNT(*) FROM poi_bot.users WHERE created_at >= ?", Timestamp.from(since))

    /** Пользователи, у которых есть день активности с [fromDay] включительно. */
    fun countActiveSince(fromDay: LocalDate): Long =
        count("SELECT COUNT(DISTINCT user_id) FROM poi_bot.user_activity_day WHERE day >= ?", Date.valueOf(fromDay))

    fun content(now: Instant): ContentStats = jdbc.queryForObject(
        """
        SELECT (SELECT COUNT(*) FROM poi_bot.saved_place),
               (SELECT COUNT(DISTINCT place_id) FROM poi_bot.saved_place),
               (SELECT COUNT(*) FROM poi_bot.chat WHERE is_active AND type IN ('group', 'supergroup')),
               (SELECT COUNT(*) FROM poi_bot.saved_place_chat),
               (SELECT COUNT(*) FROM poi_bot.place_rating),
               (SELECT COUNT(*) FROM poi_bot.place_comment WHERE NOT hidden),
               (SELECT COUNT(*) FROM poi_bot.place_comment WHERE hidden),
               (SELECT COUNT(*) FROM poi_bot.place_share_token WHERE expires_at IS NULL OR expires_at > ?)
        """.trimIndent(),
        { rs, _ ->
            ContentStats(
                savedPlaces = rs.getLong(1), uniquePlaces = rs.getLong(2), groups = rs.getLong(3),
                publications = rs.getLong(4), ratings = rs.getLong(5), comments = rs.getLong(6),
                hiddenComments = rs.getLong(7), activeLinks = rs.getLong(8),
            )
        },
        Timestamp.from(now),
    )!!

    /** Понедельник недели (МСК) -> число новых пользователей, начиная с [since]. */
    fun newUsersByWeek(since: Instant): Map<LocalDate, Long> = byWeek(
        "SELECT date_trunc('week', created_at AT TIME ZONE 'Europe/Moscow')::date, COUNT(*) " +
                "FROM poi_bot.users WHERE created_at >= ? GROUP BY 1",
        Timestamp.from(since),
    )

    fun newPlacesByWeek(since: Instant): Map<LocalDate, Long> = byWeek(
        "SELECT date_trunc('week', created_at AT TIME ZONE 'Europe/Moscow')::date, COUNT(*) " +
                "FROM poi_bot.saved_place WHERE created_at >= ? GROUP BY 1",
        Timestamp.from(since),
    )

    /** Понедельник недели -> число активных пользователей, начиная с дня [fromDay]. */
    fun activeUsersByWeek(fromDay: LocalDate): Map<LocalDate, Long> = byWeek(
        "SELECT date_trunc('week', day)::date, COUNT(DISTINCT user_id) " +
                "FROM poi_bot.user_activity_day WHERE day >= ? GROUP BY 1",
        Date.valueOf(fromDay),
    )

    private fun count(sql: String, vararg args: Any): Long = jdbc.queryForObject(sql, Long::class.java, *args) ?: 0L

    private fun byWeek(sql: String, arg: Any): Map<LocalDate, Long> =
        jdbc.query(sql, { rs, _ -> rs.getDate(1).toLocalDate() to rs.getLong(2) }, arg).toMap()
}
