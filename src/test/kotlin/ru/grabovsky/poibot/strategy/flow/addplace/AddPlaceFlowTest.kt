package ru.grabovsky.poibot.strategy.flow.addplace

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.Venue
import org.telegram.telegrambots.meta.api.objects.location.Location
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.service.interfaces.*
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.places.PlaceFormatter
import java.util.*
import org.telegram.telegrambots.meta.api.objects.User as TgUser

/** Заглушка i18n: возвращает ключ, чтобы проверять, какое сообщение выбрано. */
private class KeyI18n : I18nService {
    override fun i18n(code: String, locale: Locale, default: String?, vararg args: Any?) = code
}

class AddPlaceFlowTest : ShouldSpec({
    val locale = Locale.forLanguageTag("ru")
    val savedPlaceService = mockk<SavedPlaceService>()
    val matching = mockk<PlaceMatchingService>()
    val i18n = KeyI18n()
    val cardFactory = mockk<ru.grabovsky.poibot.strategy.flow.places.PlaceCardFactory>()
    val flow = AddPlaceFlow(savedPlaceService, matching, PlaceFormatter(i18n), i18n, cardFactory, io.micrometer.core.instrument.simple.SimpleMeterRegistry())
    val tgUser = mockk<TgUser> { every { id } returns 7L }

    beforeTest { clearMocks(savedPlaceService, matching) }

    fun context(state: AddPlaceState, bindings: Map<String, Int> = mapOf("form" to 10)) =
        FlowContext(tgUser, locale, FlowStateHolder(AddPlaceStep.FORM.key, state, bindings))

    fun callback(): CallbackQuery = mockk {
        every { id } returns "cb"
        every { from } returns tgUser
    }

    fun message(
        text: String? = null,
        venue: Venue? = null,
        location: Location? = null,
    ): Message = mockk {
        every { messageId } returns 55
        every { this@mockk.text } returns text
        every { this@mockk.venue } returns venue
        every { this@mockk.location } returns location
        every { hasLocation() } returns (location != null)
        every { hasPhoto() } returns false
    }

    fun location(lat: Double, lon: Double): Location = mockk {
        every { latitude } returns lat
        every { longitude } returns lon
    }

    should("send an empty form on start") {
        val result = flow.start(FlowStartContext(tgUser, locale))

        result.stepKey shouldBe "form"
        result.actions.single().shouldBeInstanceOf<SendMessageAction>()
    }

    should("preload the record when started with EDIT argument") {
        every { savedPlaceService.get(7L, 5L) } returns
                SavedPlace(id = 5L, ownerId = 7L, placeId = 1L, name = "Хмель", lat = 1.0, lon = 2.0)

        val result = flow.start(FlowStartContext(tgUser, locale, "EDIT:5"))

        result.payload.editingId shouldBe 5L
        result.payload.name shouldBe "Хмель"
        result.payload.originalLat shouldBe 1.0
    }

    should("put first plain text into the name and refresh the form") {
        val state = AddPlaceState()

        val result = flow.onMessage(context(state), message(text = "Хмель"))

        result.shouldNotBeNull()
        state.name shouldBe "Хмель"
        result.actions.shouldContain(DeleteMessageIdAction(55))
        result.actions.any { it is EditMessageAction && it.bindingKey == "form" } shouldBe true
    }

    should("fill name, address and location from a venue") {
        val venue = mockk<Venue> {
            every { title } returns "Бар Хмель"
            every { address } returns "Ленина 5"
            every { this@mockk.location } returns location(55.0, 37.0)
        }
        val state = AddPlaceState()

        flow.onMessage(context(state), message(venue = venue))

        state.name shouldBe "Бар Хмель"
        state.address shouldBe "Ленина 5"
        state.lat shouldBe 55.0
        state.lon shouldBe 37.0
    }

    should("set the personal status directly and clear it by pressing the active one again") {
        val state = AddPlaceState(name = "Хмель")

        flow.onCallback(context(state), callback(), "STATUS:BEEN")
        state.status shouldBe "BEEN"
        flow.onCallback(context(state), callback(), "STATUS:WANT")
        state.status shouldBe "WANT"
        flow.onCallback(context(state), callback(), "STATUS:WANT")
        state.status shouldBe null
    }

    should("parse tags typed after the tags button and keep an invalid input as a prompt") {
        val state = AddPlaceState(name = "Хмель", awaitingField = "tags")

        flow.onMessage(context(state), message(text = "#Бар, Крафт"))
        state.tags shouldBe listOf("бар", "крафт")

        state.awaitingField = "tags"
        val invalid = flow.onMessage(context(state), message(text = "a b c d e f"))
        invalid.shouldNotBeNull()
        state.tags shouldBe listOf("бар", "крафт")
        state.awaitingField shouldBe "tags"
    }

    should("append typed tags to the existing ones without duplicates") {
        val state = AddPlaceState(name = "Хмель", awaitingField = "tags", tags = mutableListOf("пиво", "бар"))

        flow.onMessage(context(state), message(text = "#Чешский бар"))

        state.tags shouldBe listOf("пиво", "бар", "чешский")
    }

    should("save the note typed after the note button") {
        val state = AddPlaceState(name = "Хмель", awaitingField = "note")

        flow.onMessage(context(state), message(text = "Брать столик у окна"))

        state.note shouldBe "Брать столик у окна"
    }

    should("suggest the user's popular tags first and add a picked suggestion") {
        every { savedPlaceService.popularTags(7L) } returns listOf("вино", "бар")
        val state = AddPlaceState(name = "Хмель", tags = mutableListOf("вино"))

        flow.onCallback(context(state), callback(), "FIELD:tags")
        // «вино» уже выбрано, остаётся «бар», а затем стартовый набор (в тесте i18n возвращает ключ)
        state.tagSuggestions shouldBe listOf("бар", "tags.defaults")

        val picked = flow.onCallback(context(state), callback(), "TAG:0")

        picked.shouldNotBeNull()
        state.tags shouldBe listOf("вино", "бар")
        state.awaitingField shouldBe null
    }

    should("pass the personal fields to the saved record") {
        val draft = slot<SavedPlaceDraft>()
        every { savedPlaceService.create(7L, capture(draft), null) } returns
                SavedPlace(id = 1L, ownerId = 7L, placeId = 1L, name = "Хмель")
        val state = AddPlaceState(name = "Хмель", status = "WANT", note = "Столик у окна", tags = mutableListOf("бар"))

        flow.onCallback(context(state), callback(), "SAVE")

        draft.captured.status shouldBe "WANT"
        draft.captured.note shouldBe "Столик у окна"
        draft.captured.tags shouldBe listOf("бар")
    }

    should("recognize a link and put it into the website") {
        val state = AddPlaceState(name = "Хмель")

        flow.onMessage(context(state), message(text = "khmel.ru"))

        state.websiteUrl shouldBe "https://khmel.ru"
    }

    should("ask which field to use when text is ambiguous") {
        val state = AddPlaceState(name = "Хмель")

        val result = flow.onMessage(context(state), message(text = "Уютное место с крафтовым пивом"))

        result.shouldNotBeNull()
        result.stepKey shouldBe "field_choice"
        state.pendingText shouldBe "Уютное место с крафтовым пивом"
        state.pendingMessageId shouldBe 55
    }

    should("write pending text into chosen field") {
        val state = AddPlaceState(name = "Хмель", pendingText = "Уютное место", pendingMessageId = 55)

        val result = flow.onCallback(context(state), callback(), "PUT:description")

        result.shouldNotBeNull()
        state.description shouldBe "Уютное место"
        state.pendingText shouldBe null
        result.stepKey shouldBe "form"
    }

    should("reject saving without a name") {
        val result = flow.onCallback(context(AddPlaceState()), callback(), "SAVE")

        result.shouldNotBeNull()
        val alert = result.actions.filterIsInstance<AnswerCallbackAction>().single()
        alert.showAlert shouldBe true
        alert.text shouldBe "alerts.add.name_required"
        verify(exactly = 0) { savedPlaceService.create(any(), any(), any()) }
    }

    should("save immediately when there is no location") {
        val state = AddPlaceState(name = "Хмель")
        every { savedPlaceService.create(7L, any(), null) } returns
                SavedPlace(id = 1L, ownerId = 7L, placeId = 1L, name = "Хмель")

        val result = flow.onCallback(context(state), callback(), "SAVE")

        result.shouldNotBeNull()
        result.completed shouldBe true
        verify { savedPlaceService.create(7L, match { it.name == "Хмель" }, null) }
    }

    should("ask to confirm a strong candidate before saving") {
        val state = AddPlaceState(name = "Хмель", lat = 55.0, lon = 37.0)
        every { matching.findCandidates(55.0, 37.0, "Хмель") } returns
                listOf(PlaceCandidate(5L, "Бар Хмель", "Ленина 5", 20, 1.0))

        val result = flow.onCallback(context(state), callback(), "SAVE")

        result.shouldNotBeNull()
        result.stepKey shouldBe "resolve"
        result.completed shouldBe false
        state.resolveStrong shouldBe true
        state.candidates shouldHaveSize 1
        verify(exactly = 0) { savedPlaceService.create(any(), any(), any()) }
    }

    should("save linked to the chosen candidate") {
        val state = AddPlaceState(name = "Хмель", lat = 55.0, lon = 37.0)
        val link = slot<Long>()
        every { savedPlaceService.create(7L, any(), capture(link)) } returns
                SavedPlace(id = 1L, ownerId = 7L, placeId = 5L, name = "Хмель")

        val result = flow.onCallback(context(state), callback(), "LINK:5")

        result.shouldNotBeNull()
        result.completed shouldBe true
        link.captured shouldBe 5L
    }

    should("offer the remaining candidates after rejecting the strong one") {
        val state = AddPlaceState(
            name = "Хмель", lat = 55.0, lon = 37.0, resolveStrong = true,
            candidates = mutableListOf(CandidateDto(5L, "Хмель", null, 10, 1.0), CandidateDto(6L, "Другой", null, 30, 0.2)),
        )

        val result = flow.onCallback(context(state), callback(), "NO")

        result.shouldNotBeNull()
        result.completed shouldBe false
        state.resolveStrong shouldBe false
        state.candidates.map { it.placeId } shouldBe listOf(6L)
    }

    should("save as a new place when the only strong candidate is rejected") {
        val state = AddPlaceState(
            name = "Хмель", lat = 55.0, lon = 37.0, resolveStrong = true,
            candidates = mutableListOf(CandidateDto(5L, "Хмель", null, 10, 1.0)),
        )
        every { savedPlaceService.create(7L, any(), null) } returns
                SavedPlace(id = 1L, ownerId = 7L, placeId = 9L, name = "Хмель")

        val result = flow.onCallback(context(state), callback(), "NO")

        result.shouldNotBeNull()
        result.completed shouldBe true
    }

    should("not ask about candidates when editing without changing the location") {
        val state = AddPlaceState(
            editingId = 3L, name = "Хмель", lat = 55.0, lon = 37.0, originalLat = 55.0, originalLon = 37.0,
        )
        every { savedPlaceService.update(7L, 3L, any(), null) } returns
                SavedPlace(id = 3L, ownerId = 7L, placeId = 1L, name = "Хмель")

        val result = flow.onCallback(context(state), callback(), "SAVE")

        result.shouldNotBeNull()
        result.completed shouldBe true
        verify(exactly = 0) { matching.findCandidates(any(), any(), any()) }
    }

    should("refresh the opened card after saving an edit") {
        val refresh = mockk<FlowAction>()
        val state = AddPlaceState(
            editingId = 3L, name = "Хмель",
            card = ru.grabovsky.poibot.strategy.flow.places.CardRef(messageId = 42, savedId = 3L),
        )
        val saved = SavedPlace(id = 3L, ownerId = 7L, placeId = 1L, name = "Хмель")
        every { savedPlaceService.update(7L, 3L, any(), null) } returns saved
        every { cardFactory.refreshAction(state.card!!, saved, locale) } returns refresh

        val result = flow.onCallback(context(state), callback(), "SAVE")

        result.shouldNotBeNull()
        result.actions.shouldContain(refresh)
    }

    should("show an alert when the per-user limit is reached") {
        val state = AddPlaceState(name = "Хмель")
        every { savedPlaceService.create(7L, any(), null) } throws PlaceLimitExceededException(500)

        val result = flow.onCallback(context(state), callback(), "SAVE")

        result.shouldNotBeNull()
        result.completed shouldBe false
        result.actions.filterIsInstance<AnswerCallbackAction>().single().text shouldBe "alerts.add.limit"
    }

    should("complete and delete the form on cancel") {
        val result = flow.onCallback(context(AddPlaceState()), callback(), "CANCEL")

        result.shouldNotBeNull()
        result.completed shouldBe true
        result.actions.shouldContain(DeleteMessageAction("form"))
    }

    should("count shown and rejected candidates for the duplicate-places metric") {
        val registry = io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        val metered = AddPlaceFlow(savedPlaceService, matching, PlaceFormatter(i18n), i18n, cardFactory, registry)
        val state = AddPlaceState(name = "Хмель", lat = 55.0, lon = 37.0)
        every { matching.findCandidates(55.0, 37.0, "Хмель") } returns listOf(PlaceCandidate(5L, "Хмель", null, 10, 1.0))
        every { savedPlaceService.create(7L, any(), null) } returns SavedPlace(id = 1L, ownerId = 7L, placeId = 9L, name = "Хмель")

        metered.onCallback(context(state), callback(), "SAVE")
        metered.onCallback(context(state), callback(), "NO")

        registry.counter("poi.place.resolve.shown", "kind", "strong").count() shouldBe 1.0
        registry.counter("poi.place.resolve.result", "result", "rejected").count() shouldBe 1.0
        registry.counter("poi.place.resolve.result", "result", "linked").count() shouldBe 0.0
    }
})
