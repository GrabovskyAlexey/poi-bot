package ru.grabovsky.poibot.strategy.flow

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import ru.grabovsky.poibot.entity.User
import ru.grabovsky.poibot.entity.UserProfile
import ru.grabovsky.poibot.service.interfaces.StatsService
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.stats.AudienceStats
import ru.grabovsky.poibot.stats.ContentStats
import ru.grabovsky.poibot.stats.StatsSnapshot
import ru.grabovsky.poibot.stats.WeekStats
import ru.grabovsky.poibot.strategy.flow.admin.AdminFlow
import ru.grabovsky.poibot.strategy.flow.admin.AdminState
import ru.grabovsky.poibot.strategy.flow.admin.AdminStep
import ru.grabovsky.poibot.strategy.flow.admin.AdminView
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import java.time.LocalDate
import java.util.*
import org.telegram.telegrambots.meta.api.objects.User as TgUser

class AdminFlowTest : ShouldSpec({
    val locale = Locale.forLanguageTag("ru")
    val userService = mockk<UserService>()
    val statsService = mockk<StatsService>()
    val flow = AdminFlow(statsService, userService, KeyI18n())
    val tgUser = mockk<TgUser> { every { id } returns 7L }

    val snapshot = StatsSnapshot(
        AudienceStats(total = 20, withPlaces = 5, new1 = 1, new7 = 2, new30 = 3, active1 = 4, active7 = 5, active30 = 6),
        ContentStats(10, 8, 2, 5, 4, 3, 1, 6),
        listOf(WeekStats(LocalDate.parse("2026-10-05"), 2, 6, 7), WeekStats(LocalDate.parse("2026-10-12"), 1, 4, 3)),
    )

    fun user(admin: Boolean) = User(7L, "A", null, "a").apply { profile = UserProfile(userId = 7L, user = this, isAdmin = admin) }

    fun context() = FlowContext(tgUser, locale, FlowStateHolder(AdminStep.MAIN.key, AdminState(), mapOf("main" to 10)))

    fun callback(): CallbackQuery = mockk {
        every { id } returns "cb"
        every { from } returns tgUser
    }

    beforeTest {
        clearMocks(userService, statsService)
        every { statsService.snapshot(any()) } returns snapshot
    }

    should("show statistics to an admin") {
        every { userService.getUser(7L) } returns user(admin = true)

        val result = flow.start(FlowStartContext(tgUser, locale))

        result.completed shouldBe false
        val message = result.actions.single().shouldBeInstanceOf<SendMessageAction>().message
        message.inlineButtons.map { it.payload.data } shouldBe listOf("REFRESH", "CLOSE")
        val view = message.model.shouldBeInstanceOf<AdminView>()
        view.withPlacesPercent shouldBe 25
        view.weekTable shouldContain "05.10"
        view.weekTable shouldContain "12.10"
    }

    should("silently ignore anyone who is not an admin") {
        every { userService.getUser(7L) } returns user(admin = false)

        val result = flow.start(FlowStartContext(tgUser, locale))

        result.actions shouldBe emptyList()
        result.completed shouldBe true
        flow.onCallback(context(), callback(), "REFRESH") shouldBe null
    }

    should("recalculate on refresh and delete the message on close") {
        every { userService.getUser(7L) } returns user(admin = true)

        val refresh = flow.onCallback(context(), callback(), "REFRESH")!!
        refresh.actions.filterIsInstance<EditMessageAction>().size shouldBe 1

        val close = flow.onCallback(context(), callback(), "CLOSE")!!
        close.completed shouldBe true
        close.actions.filterIsInstance<DeleteMessageAction>().size shouldBe 1
    }
})
