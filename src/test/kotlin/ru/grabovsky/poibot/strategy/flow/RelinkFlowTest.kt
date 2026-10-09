package ru.grabovsky.poibot.strategy.flow

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.service.interfaces.PlaceCandidate
import ru.grabovsky.poibot.service.interfaces.PlaceLinkService
import ru.grabovsky.poibot.service.interfaces.RelinkResult
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.places.PlaceFormatter
import ru.grabovsky.poibot.strategy.flow.relink.RelinkFlow
import ru.grabovsky.poibot.strategy.flow.relink.RelinkState
import java.util.*
import org.telegram.telegrambots.meta.api.objects.User as TgUser

class RelinkFlowTest : ShouldSpec({
    val locale = Locale.forLanguageTag("ru")
    val links = mockk<PlaceLinkService>()
    val i18n = KeyI18n()
    val formatter = PlaceFormatter(i18n)
    val reviews = mockk<ru.grabovsky.poibot.service.interfaces.ReviewService> {
        every { summary(any()) } returns ru.grabovsky.poibot.service.interfaces.RatingSummary.EMPTY
        every { commentCount(any()) } returns 0
        every { userComment(any(), any()) } returns null
    }
    val savedPlaces = mockk<ru.grabovsky.poibot.service.interfaces.SavedPlaceService>()
    val flow = RelinkFlow(
        links, formatter,
        ru.grabovsky.poibot.strategy.flow.places.PlaceCardFactory(i18n, formatter, reviews, links),
        savedPlaces, i18n,
    )
    val tgUser = mockk<TgUser> { every { id } returns 1L }

    beforeTest { clearMocks(links) }

    fun ctx() = FlowContext(tgUser, locale, FlowStateHolder("pick", RelinkState(), emptyMap()))

    fun callback(messageId: Int? = 33): CallbackQuery = mockk {
        every { id } returns "cb"
        every { from } returns tgUser
        every { message } returns messageId?.let { id -> mockk<Message> { every { this@mockk.messageId } returns id } }
    }

    should("list candidates as buttons") {
        every { links.candidatesFor(1L, 5L) } returns listOf(
            PlaceCandidate(70L, "Хмель", "Ленина 5", 20, 0.9),
            PlaceCandidate(71L, "Другой", null, 60, 0.1),
        )

        val result = flow.onCallback(ctx(), callback(), "OPEN:5")

        val send = result.shouldNotBeNull().actions.filterIsInstance<SendMessageAction>().single()
        send.message.inlineButtons.map { it.payload.data } shouldBe listOf("LINK:5:70", "LINK:5:71", "CLOSE")
        send.message.inlineButtons.last().row shouldBe FlowInlineButton.LAST_ROW
        result.payload.savedPlaceId shouldBe 5L
    }

    should("say there is nothing to merge with when no other places are nearby") {
        every { links.candidatesFor(1L, 5L) } returns emptyList()

        val result = flow.onCallback(ctx(), callback(), "OPEN:5")

        val answer = result.shouldNotBeNull().actions.filterIsInstance<AnswerCallbackAction>().single()
        answer.text shouldBe "alerts.relink.none"
        answer.showAlert shouldBe true
    }

    should("relink and close the picker") {
        every { links.relink(1L, 5L, 70L) } returns RelinkResult.RELINKED

        val result = flow.onCallback(ctx(), callback(33), "LINK:5:70")

        result.shouldNotBeNull().actions.shouldContain(DeleteMessageIdAction(33))
        result.actions.filterIsInstance<AnswerCallbackAction>().single().text shouldBe "alerts.relink.done"
    }

    should("alert when the place cannot be relinked") {
        every { links.relink(1L, 5L, 70L) } returns RelinkResult.NOT_FOUND

        val result = flow.onCallback(ctx(), callback(), "LINK:5:70")

        result.shouldNotBeNull().actions.filterIsInstance<AnswerCallbackAction>().single().showAlert shouldBe true
    }

    should("close the picker") {
        flow.onCallback(ctx(), callback(33), "CLOSE").shouldNotBeNull().actions.shouldContain(DeleteMessageIdAction(33))
    }

    should("redraw the card from which the picker was opened after relinking") {
        every { links.candidatesFor(1L, 5L) } returns listOf(PlaceCandidate(70L, "Хмель", null, 20, 0.9))
        every { links.relink(1L, 5L, 70L) } returns RelinkResult.RELINKED
        every { savedPlaces.get(1L, 5L) } returns
                ru.grabovsky.poibot.entity.SavedPlace(id = 5L, ownerId = 1L, placeId = 70L, name = "Хмель")
        val state = RelinkState()
        val context = FlowContext(tgUser, locale, FlowStateHolder("pick", state, emptyMap()))
        flow.onCallback(context, callback(25), "OPEN:5:P:0")

        val result = flow.onCallback(context, callback(33), "LINK:5:70")

        val refresh = result.shouldNotBeNull().actions.filterIsInstance<EditCardAction>().single()
        refresh.messageId shouldBe 25
        result.actions.shouldContain(DeleteMessageIdAction(33))
    }
})
