package ru.grabovsky.poibot.strategy.flow

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import ru.grabovsky.poibot.config.BotConfig
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.service.interfaces.AcceptResult
import ru.grabovsky.poibot.service.interfaces.SavedPlaceService
import ru.grabovsky.poibot.service.interfaces.SharingService
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.places.PlaceFormatter
import ru.grabovsky.poibot.strategy.flow.shared.SharedFlow
import ru.grabovsky.poibot.strategy.flow.shared.SharedOutView
import ru.grabovsky.poibot.strategy.flow.shared.SharedState
import java.util.*
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.User as TgUser

class SharedFlowTest : ShouldSpec({
    val locale = Locale.forLanguageTag("ru")
    val sharing = mockk<SharingService>()
    val savedPlaces = mockk<SavedPlaceService>()
    val reviewService = mockk<ru.grabovsky.poibot.service.interfaces.ReviewService> {
        every { summary(any()) } returns ru.grabovsky.poibot.service.interfaces.RatingSummary.EMPTY
        every { commentCount(any()) } returns 0
    }
    val i18n = KeyI18n()
    val flow = SharedFlow(sharing, savedPlaces, reviewService, BotConfig("token", "PoiBot"), PlaceFormatter(i18n), i18n)
    val tgUser = mockk<TgUser> { every { id } returns 2L }

    beforeTest { clearMocks(sharing, savedPlaces) }

    val place = SavedPlace(
        id = 5L, ownerId = 1L, placeId = 77L, name = "Хмель", lat = 55.0, lon = 37.0, photoFileId = "photo",
    )

    fun callback(messageId: Int? = 33): CallbackQuery = mockk {
        every { id } returns "cb"
        every { from } returns tgUser
        every { message } returns messageId?.let { id -> mockk<Message> { every { this@mockk.messageId } returns id } }
    }

    fun ctx() = FlowContext(tgUser, locale, FlowStateHolder("in", SharedState("tok"), mapOf("main" to 1)))

    should("show the shared place as a photo card with save, map and close buttons") {
        every { sharing.findShared("tok") } returns place

        val result = flow.start(FlowStartContext(tgUser, locale, "sp_tok"))

        val photo = result.actions.single().shouldBeInstanceOf<SendPhotoAction>()
        photo.photoFileId shouldBe "photo"
        photo.message.inlineButtons.map { it.payload.data } shouldBe listOf("SAVE:tok", "MAP:tok", "CLOSE")
        photo.message.inlineButtons.last().row shouldBe FlowInlineButton.LAST_ROW
        result.completed shouldBe false
    }

    should("omit the map button for a place without location") {
        every { sharing.findShared("tok") } returns place.copy(lat = null, lon = null, photoFileId = null)

        val result = flow.start(FlowStartContext(tgUser, locale, "sp_tok"))

        val send = result.actions.single().shouldBeInstanceOf<SendMessageAction>()
        send.message.inlineButtons.map { it.payload.data } shouldBe listOf("SAVE:tok", "CLOSE")
    }

    should("show a temporary message for an invalid link") {
        every { sharing.findShared("tok") } returns null

        val result = flow.start(FlowStartContext(tgUser, locale, "sp_tok"))

        result.completed shouldBe true
        val send = result.actions.single().shouldBeInstanceOf<SendMessageAction>()
        send.message.stepKey shouldBe "invalid"
        send.message.autoDeleteAfterSeconds shouldBe 10
    }

    should("send the share link for an own place") {
        every { savedPlaces.get(2L, 5L) } returns place.copy(ownerId = 2L)
        every { sharing.getOrCreateToken(2L, 5L) } returns "tok"

        val result = flow.start(FlowStartContext(tgUser, locale, "OUT:5"))

        val send = result.actions.single().shouldBeInstanceOf<SendMessageAction>()
        val view = send.message.model.shouldBeInstanceOf<SharedOutView>()
        view.link shouldBe "https://t.me/PoiBot?start=sp_tok"
        view.name shouldBe "Хмель"
        val buttons = send.message.inlineButtons
        buttons.map { it.text } shouldBe listOf("buttons.shared.send", "buttons.places.close")
        val shareUrl = buttons.first().url
        shareUrl shouldBe "https://t.me/share/url?url=https%3A%2F%2Ft.me%2FPoiBot%3Fstart%3Dsp_tok&text=shared.message"
    }

    should("refuse to share a foreign place") {
        every { savedPlaces.get(2L, 5L) } returns null

        flow.start(FlowStartContext(tgUser, locale, "OUT:5")).completed shouldBe true
    }

    should("not start a card for orphan button callbacks") {
        val result = flow.start(FlowStartContext(tgUser, locale, "SAVE:tok"))

        result.actions shouldBe emptyList()
        result.completed shouldBe false
    }

    should("replace the card with a temporary confirmation after saving") {
        every { sharing.accept(2L, "tok") } returns AcceptResult.Saved(place.copy(id = 9L, ownerId = 2L))

        val result = flow.onCallback(ctx(), callback(), "SAVE:tok")

        result.shouldNotBeNull()
        result.actions.shouldContain(DeleteMessageIdAction(33))
        val confirmation = result.actions.filterIsInstance<SendMessageAction>().single()
        confirmation.message.stepKey shouldBe "saved"
        confirmation.message.autoDeleteAfterSeconds shouldBe 10
    }

    should("explain why a place cannot be saved") {
        mapOf(
            AcceptResult.AlreadySaved to "alerts.shared.already_saved",
            AcceptResult.OwnPlace to "alerts.shared.own_place",
            AcceptResult.LimitReached to "alerts.add.limit",
        ).forEach { (outcome, key) ->
            every { sharing.accept(2L, "tok") } returns outcome

            val result = flow.onCallback(ctx(), callback(), "SAVE:tok")

            val answer = result.shouldNotBeNull().actions.filterIsInstance<AnswerCallbackAction>().single()
            answer.text shouldBe key
            answer.showAlert shouldBe true
        }
    }

    should("remove the card when the link became invalid") {
        every { sharing.accept(2L, "tok") } returns AcceptResult.NotFound

        val result = flow.onCallback(ctx(), callback(), "SAVE:tok")

        result.shouldNotBeNull().actions.shouldContain(DeleteMessageIdAction(33))
    }

    should("send the place on the map") {
        every { sharing.findShared("tok") } returns place

        val result = flow.onCallback(ctx(), callback(), "MAP:tok")

        result.shouldNotBeNull().actions.filterIsInstance<SendVenueAction>().single().latitude shouldBe 55.0
    }

    should("close the card by deleting its message") {
        val result = flow.onCallback(ctx(), callback(), "CLOSE")

        result.shouldNotBeNull().actions.shouldContain(DeleteMessageIdAction(33))
    }

    should("send a new link message when Share is pressed and the flow already has a state") {
        every { savedPlaces.get(2L, 5L) } returns place.copy(ownerId = 2L)
        every { sharing.getOrCreateToken(2L, 5L) } returns "tok"

        val result = flow.onCallback(ctx(), callback(), "OUT:5")

        result.shouldNotBeNull()
        result.actions.filterIsInstance<SendMessageAction>().single().message.stepKey shouldBe "out"
        result.actions.filterIsInstance<AnswerCallbackAction>().size shouldBe 1
    }

    should("only acknowledge the callback that immediately follows a restart") {
        every { savedPlaces.get(2L, 5L) } returns place.copy(ownerId = 2L)
        every { sharing.getOrCreateToken(2L, 5L) } returns "tok"
        val started = flow.start(FlowStartContext(tgUser, locale, "OUT:5"))
        started.payload.fresh shouldBe true

        val first = flow.onCallback(
            FlowContext(tgUser, locale, FlowStateHolder("out", started.payload, mapOf("main" to 1))),
            callback(), "OUT:5",
        )
        first.shouldNotBeNull().actions.filterIsInstance<SendMessageAction>().size shouldBe 0
        started.payload.fresh shouldBe false

        val second = flow.onCallback(
            FlowContext(tgUser, locale, FlowStateHolder("out", started.payload, mapOf("main" to 1))),
            callback(), "OUT:5",
        )
        second.shouldNotBeNull().actions.filterIsInstance<SendMessageAction>().size shouldBe 1
    }

    should("tell the user when the place to share no longer exists") {
        every { savedPlaces.get(2L, 5L) } returns null

        val result = flow.onCallback(ctx(), callback(), "OUT:5")

        result.shouldNotBeNull().actions.filterIsInstance<AnswerCallbackAction>().single().showAlert shouldBe true
    }
})
