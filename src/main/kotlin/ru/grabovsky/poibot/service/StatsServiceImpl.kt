package ru.grabovsky.poibot.service

import org.springframework.stereotype.Service
import ru.grabovsky.poibot.repository.StatsRepository
import ru.grabovsky.poibot.repository.UserActivityRepository
import ru.grabovsky.poibot.service.interfaces.StatsService
import ru.grabovsky.poibot.stats.AudienceStats
import ru.grabovsky.poibot.stats.STATS_ZONE
import ru.grabovsky.poibot.stats.StatsSnapshot
import ru.grabovsky.poibot.stats.WeekStats
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Service
class StatsServiceImpl(
    private val stats: StatsRepository,
    private val activity: UserActivityRepository,
) : StatsService {

    override fun snapshot(now: Instant): StatsSnapshot {
        val today = now.atZone(STATS_ZONE).toLocalDate()
        fun since(days: Int) = today.minusDays(days - 1L)
        fun start(day: LocalDate) = day.atStartOfDay(STATS_ZONE).toInstant()

        val audience = AudienceStats(
            total = stats.countUsers(),
            withPlaces = stats.countUsersWithPlaces(),
            new1 = stats.countUsersCreatedSince(start(since(1))),
            new7 = stats.countUsersCreatedSince(start(since(7))),
            new30 = stats.countUsersCreatedSince(start(since(30))),
            active1 = stats.countActiveSince(since(1)),
            active7 = stats.countActiveSince(since(7)),
            active30 = stats.countActiveSince(since(30)),
        )

        val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstMonday = currentMonday.minusWeeks(StatsService.WEEKS - 1L)
        val newUsers = stats.newUsersByWeek(start(firstMonday))
        val newPlaces = stats.newPlacesByWeek(start(firstMonday))
        val active = stats.activeUsersByWeek(firstMonday)
        val weeks = (0 until StatsService.WEEKS).map { index ->
            val monday = firstMonday.plusWeeks(index.toLong())
            WeekStats(monday, newUsers[monday] ?: 0, active[monday] ?: 0, newPlaces[monday] ?: 0)
        }
        return StatsSnapshot(audience, stats.content(now), weeks)
    }

    override fun recordActivity(userId: Long, previousActionAt: Instant?, now: Instant) {
        val today = now.atZone(STATS_ZONE).toLocalDate()
        if (previousActionAt != null && previousActionAt.atZone(STATS_ZONE).toLocalDate() == today) return
        activity.touch(userId, today)
    }
}
