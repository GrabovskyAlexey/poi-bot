package ru.grabovsky.poibot.strategy.flow

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.location.Location
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.entity.User
import ru.grabovsky.poibot.entity.UserProfile
import ru.grabovsky.poibot.geo.NearbyPoint
import ru.grabovsky.poibot.geo.NearbyResult
import ru.grabovsky.poibot.geo.SearchRadius
import ru.grabovsky.poibot.service.interfaces.*
import ru.grabovsky.poibot.strategy.flow.addplace.AddPlaceState
import ru.grabovsky.poibot.strategy.flow.addplace.CandidateDto
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.core.serialization.JacksonFlowPayloadSerializer
import ru.grabovsky.poibot.strategy.flow.nearby.NearbyFlow
import ru.grabovsky.poibot.strategy.flow.nearby.NearbyState
import ru.grabovsky.poibot.strategy.flow.nearby.NearbyStep
import ru.grabovsky.poibot.strategy.flow.places.*
import java.util.*
import org.telegram.telegrambots.meta.api.objects.User as TgUser

class PlaceFlowsTest : ShouldSpec({
    val locale = Locale.forLanguageTag("ru")
    val tgUser = mockk<TgUser> { every { id } returns 7L }
    val i18n = KeyI18n()
    val formatter = PlaceFormatter(i18n)
    val reviewService = mockk<ReviewService>(relaxed = true) {
        every { summary(any()) } returns RatingSummary.EMPTY
        every { commentCount(any()) } returns 0
        every { userComment(any(), any()) } returns null
    }
    val placeLinkService = mockk<PlaceLinkService>(relaxed = true) { every { candidatesFor(any(), any()) } returns emptyList() }
    val cardFactory = PlaceCardFactory(i18n, formatter, reviewService, placeLinkService)

    fun callback(): CallbackQuery = mockk {
        every { id } returns "cb"
        every { from } returns tgUser
    }

    fun place(id: Long, name: String = "Хмель", photo: String? = null) = SavedPlace(
        id = id, ownerId = 7L, placeId = 1L, name = name, lat = 55.0, lon = 37.0, photoFileId = photo,
    )

    should("serialize flow states to JSON and back") {
        val serializer = JacksonFlowPayloadSerializer(ObjectMapper().registerKotlinModule())
        val state = AddPlaceState(
            editingId = 3L, name = "Хмель", lat = 55.0, lon = 37.0, originalLat = 55.0,
            candidates = mutableListOf(CandidateDto(5L, "Хмель", "Ленина 5", 20, 0.9)),
            promptBindings = mutableListOf("prompt_1"),
        )

        val restored = serializer.deserialize(serializer.serialize(state), AddPlaceState::class.java)

        restored shouldBe state
        serializer.deserialize(null, AddPlaceState::class.java) shouldBe AddPlaceState()
        val nearby = NearbyState(55.0, 37.0, 500)
        serializer.deserialize(serializer.serialize(nearby), NearbyState::class.java) shouldBe nearby
    }

    context("PlacesFlow") {
        val service = mockk<SavedPlaceService>()
        val flow = PlacesFlow(service, cardFactory, formatter, i18n)

        fun ctx(state: PlacesState = PlacesState()) =
            FlowContext(tgUser, locale, FlowStateHolder("list", state, mapOf("list" to 1, "card" to 2)))

        should("show the first page on start") {
            every { service.list(7L, 0, any()) } returns SavedPlacePage(listOf(place(1)), 0, 1, 1)

            val result = flow.start(FlowStartContext(tgUser, locale))

            val send = result.actions.single().shouldBeInstanceOf<SendMessageAction>()
            send.message.inlineButtons.map { it.payload.data } shouldBe listOf("OPEN:1", "ALL")
        }

        should("mark places that have a location or a photo in the list") {
        every { service.list(7L, 0, any()) } returns SavedPlacePage(
            listOf(
                place(1, "Полное", photo = "p"),
                place(2, "Без геопозиции").copy(lat = null, lon = null),
            ),
            0, 1, 2,
        )

        val send = flow.start(FlowStartContext(tgUser, locale)).actions.single() as SendMessageAction

        val view = send.message.model as PlacesListView
        view.items.map { it.hasLocation to it.hasPhoto } shouldBe listOf(true to true, false to false)
    }

    should("show pagination buttons only where there is a neighbour page") {
            every { service.list(7L, 1, any()) } returns
                    SavedPlacePage(listOf(place(9, "A")), 1, 3, 17)

            val result = flow.onCallback(ctx(), callback(), "PAGE:1")

            result.shouldNotBeNull()
            val edit = result.actions.filterIsInstance<EditMessageAction>().single()
            edit.message.inlineButtons.map { it.payload.data } shouldBe listOf("OPEN:9", "PAGE:0", "PAGE:2", "ALL")
        }

        should("open a card with a photo as a photo message") {
            every { service.get(7L, 1L) } returns place(1, photo = "file123")

            val result = flow.onCallback(ctx(), callback(), "OPEN:1")

            result.shouldNotBeNull()
            result.actions.filterIsInstance<SendPhotoAction>().single().photoFileId shouldBe "file123"
            result.actions.shouldContain(DeleteMessageAction("card"))
        }

        should("send a venue for the map button") {
            every { service.get(7L, 1L) } returns place(1)

            val result = flow.onCallback(ctx(), callback(), "MAP:1")

            result.shouldNotBeNull()
            val venue = result.actions.filterIsInstance<SendVenueAction>().single()
            venue.latitude shouldBe 55.0
            venue.title shouldBe "Хмель"
        }

        should("delete only after confirmation") {
            every { service.get(7L, 1L) } returns place(1)
            every { service.delete(7L, 1L) } returns true
            every { service.list(7L, 0, any()) } returns SavedPlacePage(emptyList(), 0, 1, 0)

            val ask = flow.onCallback(ctx(), callback(), "DELASK:1")
            ask.shouldNotBeNull()
            ask.actions.filterIsInstance<SendMessageAction>().single().message.inlineButtons
                .map { it.payload.data } shouldBe listOf("DELOK:1", "OPEN:1")

            val confirm = flow.onCallback(ctx(), callback(), "DELOK:1")
            confirm.shouldNotBeNull()
            confirm.actions.filterIsInstance<AnswerCallbackAction>().single().text shouldBe "alerts.places.deleted"
        }

        should("answer with an alert when the place does not exist") {
            every { service.get(7L, 99L) } returns null

            val result = flow.onCallback(ctx(), callback(), "OPEN:99")

            result.shouldNotBeNull()
            result.actions.filterIsInstance<AnswerCallbackAction>().single().showAlert shouldBe true
        }
    }

    context("NearbyFlow") {
        val search = mockk<NearbySearchService>()
        val savedPlaces = mockk<SavedPlaceService>()
        val userService = mockk<UserService>(relaxed = true)
        val flow = NearbyFlow(search, savedPlaces, userService, cardFactory, formatter, i18n)
        val entityUser = User(7L, "A", null, "a").apply {
            profile = UserProfile(userId = 7L, user = this)
        }

        fun ctx(state: NearbyState) =
            FlowContext(tgUser, locale, FlowStateHolder("result", state, mapOf("result" to 5)))

        fun locationMessage(): Message = mockk {
            every { hasLocation() } returns true
            every { location } returns mockk<Location> {
                every { latitude } returns 55.0
                every { longitude } returns 37.0
            }
            every { from } returns tgUser
            every { messageId } returns 77
        }

        should("ask for a location with a request-location reply button on start") {
            every { userService.getUser(7L) } returns entityUser

            val result = flow.start(FlowStartContext(tgUser, locale))

            val send = result.actions.single().shouldBeInstanceOf<SendMessageAction>()
            send.message.replyButtons.single().requestLocation shouldBe true
        }

        should("search by location and show hint buttons only for radii that add points") {
            every { userService.getUser(7L) } returns entityUser
            val points = listOf(80, 300, 350, 480, 600, 900).map {
                NearbyPoint(place(it.toLong(), "p$it"), it)
            }
            every { search.searchOwn(7L, 55.0, 37.0, SearchRadius.M250) } returns
                    NearbyResult.build(points, SearchRadius.M250)

            val result = flow.onMessage(ctx(NearbyState()), locationMessage())

            result.shouldNotBeNull()
            val send = result.actions.filterIsInstance<SendMessageAction>().single()
            send.message.inlineButtons.map { it.payload.data } shouldBe
                    listOf("OPEN:80", "R:500", "R:1000", "RADIUS")
            result.actions.shouldContain(DeleteMessageIdAction(77))
        }

        should("persist the radius chosen by the user") {
            every { userService.getUser(7L) } returns entityUser
            every { search.searchOwn(7L, 55.0, 37.0, SearchRadius.M500) } returns
                    NearbyResult.build(emptyList(), SearchRadius.M500)

            val result = flow.onCallback(ctx(NearbyState(55.0, 37.0, 250)), callback(), "R:500")

            result.shouldNotBeNull()
            result.payload.radiusMeters shouldBe 500
            entityUser.profile!!.settings.searchRadiusMeters shouldBe 500
            result.stepKey shouldBe NearbyStep.RESULT.key
        }

        should("ignore radius change before any search") {
            flow.onCallback(ctx(NearbyState()), callback(), "R:500") shouldBe null
        }

        should("ignore messages without a location") {
            val message = mockk<Message> {
                every { hasLocation() } returns false
                every { venue } returns null
            }

            flow.onMessage(ctx(NearbyState()), message) shouldBe null
        }
    }

    should("show the comments button and rating only when reviews exist") {
        every { reviewService.summary(1L) } returns RatingSummary(4.5, 2)
        every { reviewService.commentCount(1L) } returns 3
        every { placeLinkService.candidatesFor(7L, 1L) } returns listOf(PlaceCandidate(2L, "Другое", null, 30, 0.1))
        val card = place(1)

        val actions = cardFactory.cardActions(FlowKeys.PLACES, card.copy(ownerId = 7L), locale, "card", manage = true)

        val send = actions.single() as SendMessageAction
        val data = send.message.inlineButtons.map { it.payload.flow to it.payload.data }
        data shouldContain ("REVIEWS" to "RATE:1:P:0")
        data shouldContain ("REVIEWS" to "ADDC:1:P:0")
        data shouldContain ("REVIEWS" to "COM:1:0:1:P:0")
        data shouldContain ("RELINK" to "OPEN:1:P:0")
        (send.message.model as PlaceCardView).rating shouldBe "4.5 (2)"
    }

    should("switch the comment button to editing when the user already commented") {
        every { reviewService.summary(1L) } returns RatingSummary.EMPTY
        every { reviewService.commentCount(1L) } returns 1
        every { reviewService.userComment(1L, 7L) } returns CommentItem(8L, "мой", null, true)
        every { placeLinkService.candidatesFor(7L, 1L) } returns emptyList()

        val actions = cardFactory.cardActions(FlowKeys.PLACES, place(1).copy(ownerId = 7L), locale, "card", manage = true)

        val buttons = (actions.single() as SendMessageAction).message.inlineButtons
        buttons.map { it.text } shouldContain "buttons.reviews.comment_edit"
        buttons.map { it.text }.contains("buttons.reviews.comment") shouldBe false
    }

    should("keep delete and close in the very last row whatever rows are added above") {
        every { reviewService.summary(1L) } returns RatingSummary(4.0, 1)
        every { reviewService.commentCount(1L) } returns 2
        every { reviewService.userComment(1L, 7L) } returns null
        every { placeLinkService.candidatesFor(7L, 1L) } returns listOf(PlaceCandidate(2L, "Другое", null, 30, 0.1))

        val manage = cardFactory.cardActions(FlowKeys.PLACES, place(1).copy(ownerId = 7L), locale, "card", manage = true)
        val nearby = cardFactory.cardActions(FlowKeys.NEARBY, place(1).copy(ownerId = 7L), locale, "card", manage = false)

        listOf(manage, nearby).forEach { actions ->
            val buttons = (actions.single() as SendMessageAction).message.inlineButtons
            val lastRow = buttons.maxOf { it.row }
            lastRow shouldBe FlowInlineButton.LAST_ROW
            val bottom = buttons.filter { it.row == lastRow }.map { it.payload.data.substringBefore(":") }
            bottom.all { it == "DELASK" || it == "CLOSE" } shouldBe true
            buttons.filter { it.row < lastRow }.none { it.payload.data == "CLOSE" || it.payload.data.startsWith("DELASK") } shouldBe true
        }
        val manageBottom = (manage.single() as SendMessageAction).message.inlineButtons.filter { it.row == FlowInlineButton.LAST_ROW }
        manageBottom.map { it.payload.data.substringBefore(":") } shouldBe listOf("DELASK", "CLOSE")
    }

    should("hide comments and relink buttons when there is nothing to show") {
        every { reviewService.summary(1L) } returns RatingSummary.EMPTY
        every { reviewService.commentCount(1L) } returns 0
        every { placeLinkService.candidatesFor(7L, 1L) } returns emptyList()

        val actions = cardFactory.cardActions(FlowKeys.PLACES, place(1).copy(ownerId = 7L), locale, "card", manage = true)

        val data = (actions.single() as SendMessageAction).message.inlineButtons.map { it.payload.data }
        data.none { it.startsWith("COM:") || it.startsWith("OPEN:") } shouldBe true
        data shouldContain "RATE:1:P:0"
    }
})
