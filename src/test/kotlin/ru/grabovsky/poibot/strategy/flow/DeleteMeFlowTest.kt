package ru.grabovsky.poibot.strategy.flow

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import ru.grabovsky.poibot.service.interfaces.I18nService
import ru.grabovsky.poibot.service.interfaces.UserDataService
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.deleteme.DeleteMeFlow
import ru.grabovsky.poibot.strategy.flow.deleteme.DeleteMeState
import ru.grabovsky.poibot.strategy.flow.deleteme.DeleteMeStep
import java.util.*
import org.telegram.telegrambots.meta.api.objects.User as TgUser

class DeleteMeFlowTest : ShouldSpec({
    val locale = Locale.forLanguageTag("ru")
    val userData = mockk<UserDataService>(relaxed = true)
    val i18n = object : I18nService {
        override fun i18n(code: String, locale: Locale, default: String?, vararg args: Any?) = code
    }
    val flow = DeleteMeFlow(userData, i18n)
    val tgUser = mockk<TgUser> { every { id } returns 7L }

    beforeTest { clearMocks(userData) }

    fun context(step: DeleteMeStep) =
        FlowContext(tgUser, locale, FlowStateHolder(step.key, DeleteMeState(), mapOf("main" to 10)))

    fun callback(): CallbackQuery = mockk {
        every { id } returns "cb"
        every { from } returns tgUser
    }

    should("show the menu on start") {
        val result = flow.start(FlowStartContext(tgUser, locale))

        result.stepKey shouldBe "menu"
        result.actions.single().shouldBeInstanceOf<SendMessageAction>()
    }

    should("send the exported data as a document") {
        every { userData.export(7L) } returns "{}".toByteArray()

        val result = flow.onCallback(context(DeleteMeStep.MENU), callback(), "EXPORT")!!

        val document = result.actions.filterIsInstance<SendDocumentAction>().single()
        document.fileName shouldBe "poibot-data.json"
        verify(exactly = 0) { userData.deleteAll(any()) }
    }

    should("ask for confirmation before deleting") {
        val result = flow.onCallback(context(DeleteMeStep.MENU), callback(), "ASK")!!

        result.stepKey shouldBe "confirm"
        verify(exactly = 0) { userData.deleteAll(any()) }
    }

    should("delete everything only on the confirmation step") {
        val result = flow.onCallback(context(DeleteMeStep.CONFIRM), callback(), "DO")!!

        result.completed shouldBe true
        verify { userData.deleteAll(7L) }
    }

    should("ignore a stale delete button when the confirmation step is not active") {
        val result = flow.onCallback(context(DeleteMeStep.MENU), callback(), "DO")!!

        result.completed shouldBe false
        verify(exactly = 0) { userData.deleteAll(any()) }
    }

    should("return to the menu when the deletion is cancelled") {
        val result = flow.onCallback(context(DeleteMeStep.CONFIRM), callback(), "BACK")!!

        result.stepKey shouldBe "menu"
        verify(exactly = 0) { userData.deleteAll(any()) }
    }
})
