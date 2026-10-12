package ru.grabovsky.poibot.service

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import ru.grabovsky.poibot.repository.StatsRepository
import ru.grabovsky.poibot.repository.UserActivityRepository
import ru.grabovsky.poibot.stats.ContentStats
import java.time.Instant
import java.time.LocalDate

class StatsServiceImplTest : ShouldSpec({
    val stats = mockk<StatsRepository>()
    val activity = mockk<UserActivityRepository>(relaxed = true)
    val service = StatsServiceImpl(stats, activity)

    // Среда 14.10.2026, 12:00 по Москве; понедельник этой недели - 12.10
    val now = Instant.parse("2026-10-14T09:00:00Z")
    val content = ContentStats(10, 8, 2, 5, 4, 3, 1, 6)

    beforeTest {
        clearMocks(stats, activity)
        every { stats.countUsers() } returns 20
        every { stats.countUsersWithPlaces() } returns 12
        every { stats.countUsersCreatedSince(any()) } returns 0
        every { stats.countActiveSince(any()) } returns 0
        every { stats.content(now) } returns content
        every { stats.newUsersByWeek(any()) } returns emptyMap()
        every { stats.newPlacesByWeek(any()) } returns emptyMap()
        every { stats.activeUsersByWeek(any()) } returns emptyMap()
    }

    should("count periods from the start of the Moscow day including today") {
        every { stats.countUsersCreatedSince(Instant.parse("2026-10-13T21:00:00Z")) } returns 1
        every { stats.countUsersCreatedSince(Instant.parse("2026-10-07T21:00:00Z")) } returns 4
        every { stats.countUsersCreatedSince(Instant.parse("2026-09-14T21:00:00Z")) } returns 9
        every { stats.countActiveSince(LocalDate.parse("2026-10-14")) } returns 3
        every { stats.countActiveSince(LocalDate.parse("2026-10-08")) } returns 7
        every { stats.countActiveSince(LocalDate.parse("2026-09-15")) } returns 11

        val audience = service.snapshot(now).audience

        audience.total shouldBe 20
        audience.withPlaces shouldBe 12
        listOf(audience.new1, audience.new7, audience.new30) shouldBe listOf(1L, 4L, 9L)
        listOf(audience.active1, audience.active7, audience.active30) shouldBe listOf(3L, 7L, 11L)
    }

    should("return eight weeks ending with the current one and fill gaps with zeros") {
        every { stats.newUsersByWeek(any()) } returns mapOf(LocalDate.parse("2026-10-12") to 3L, LocalDate.parse("2026-10-05") to 2L)
        every { stats.activeUsersByWeek(LocalDate.parse("2026-08-24")) } returns mapOf(LocalDate.parse("2026-10-12") to 5L)
        every { stats.newPlacesByWeek(any()) } returns mapOf(LocalDate.parse("2026-10-12") to 9L)

        val snapshot = service.snapshot(now)

        snapshot.weeks shouldHaveSize 8
        snapshot.weeks.first().weekStart shouldBe LocalDate.parse("2026-08-24")
        snapshot.weeks.first().newUsers shouldBe 0
        snapshot.weeks.last().let {
            it.weekStart shouldBe LocalDate.parse("2026-10-12")
            listOf(it.newUsers, it.activeUsers, it.newPlaces) shouldBe listOf(3L, 5L, 9L)
        }
        snapshot.weeks[6].newUsers shouldBe 2
        snapshot.content shouldBe content
        verify { stats.newUsersByWeek(Instant.parse("2026-08-23T21:00:00Z")) }
    }

    should("record activity once per Moscow day") {
        service.recordActivity(7L, Instant.parse("2026-10-14T00:30:00Z"), now) // 03:30 МСК того же дня
        verify(exactly = 0) { activity.touch(any(), any()) }

        service.recordActivity(7L, Instant.parse("2026-10-13T20:59:00Z"), now) // 23:59 МСК накануне
        verify { activity.touch(7L, LocalDate.parse("2026-10-14")) }

        service.recordActivity(8L, null, now)
        verify { activity.touch(8L, LocalDate.parse("2026-10-14")) }
    }
})
