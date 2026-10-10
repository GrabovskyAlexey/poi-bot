package ru.grabovsky.poibot.strategy.flow

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import ru.grabovsky.poibot.entity.User
import ru.grabovsky.poibot.entity.UserProfile
import ru.grabovsky.poibot.entity.UserSettings
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.settings.SettingsFlow
import ru.grabovsky.poibot.strategy.flow.settings.SettingsState
import ru.grabovsky.poibot.strategy.flow.settings.SettingsStep
import java.util.*
import org.telegram.telegrambots.meta.api.objects.User as TgUser

class SettingsFlowTest : ShouldSpec({
    val locale = Locale.forLanguageTag("ru")
    val userService = mockk<UserService>(relaxed = true)
    val flow = SettingsFlow(userService, KeyI18n())
    val tgUser = mockk<TgUser> { every { id } returns 7L }

    fun user(settings: UserSettings = UserSettings(), language: String? = null) =
        User(7L, "T", null, "t").apply { profile = UserProfile(settings = settings, locale = language) }

    fun context() = FlowContext(tgUser, locale, FlowStateHolder(SettingsStep.MAIN.key, SettingsState(), mapOf("main" to 10)))

    fun callback(): CallbackQuery = mockk {
        every { id } returns "cb"
        every { from } returns tgUser
    }

    beforeTest { clearMocks(userService) }

    should("show the settings on start") {
        every { userService.getUser(7L) } returns user()

        val result = flow.start(FlowStartContext(tgUser, locale))

        result.stepKey shouldBe "main"
        val message = result.actions.single().shouldBeInstanceOf<SendMessageAction>().message
        message.inlineButtons.map { it.payload.data } shouldBe listOf("LANG", "RADIUS", "CLEAN", "CLOSE")
    }

    should("cycle the language auto - ru - en - auto") {
        SettingsFlow.nextLanguage(null) shouldBe "ru"
        SettingsFlow.nextLanguage("ru") shouldBe "en"
        SettingsFlow.nextLanguage("en") shouldBe null
    }

    should("cycle the radius and wrap around") {
        SettingsFlow.nextRadius(100) shouldBe 250
        SettingsFlow.nextRadius(1000) shouldBe 100
        SettingsFlow.nextRadius(777) shouldBe 500
    }

    should("save the new language and redraw the message") {
        val stored = user()
        every { userService.getUser(7L) } returns stored

        val result = flow.onCallback(context(), callback(), "LANG")!!

        stored.profile!!.locale shouldBe "ru"
        verify { userService.saveUser(stored) }
        result.actions.filterIsInstance<EditMessageAction>().size shouldBe 1
        result.actions.filterIsInstance<EditMessageAction>().single().message.locale shouldBe Locale.forLanguageTag("ru")
    }

    should("save the radius and toggle auto-clean") {
        val stored = user()
        every { userService.getUser(7L) } returns stored

        flow.onCallback(context(), callback(), "RADIUS")
        flow.onCallback(context(), callback(), "CLEAN")

        stored.profile!!.settings.searchRadiusMeters shouldBe 500
        stored.profile!!.settings.cleanChat shouldBe false
    }

    should("close by deleting the message and ignore unknown buttons") {
        every { userService.getUser(7L) } returns user()

        val closed = flow.onCallback(context(), callback(), "CLOSE")!!
        closed.completed shouldBe true
        flow.onCallback(context(), callback(), "UNKNOWN") shouldBe null
        verify(exactly = 0) { userService.saveUser(any()) }
    }
})
