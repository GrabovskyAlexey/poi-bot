package ru.grabovsky.poibot.strategy.flow

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.service.interfaces.*
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.places.PlaceCardFactory
import ru.grabovsky.poibot.strategy.flow.places.PlaceCardView
import ru.grabovsky.poibot.strategy.flow.places.PlaceFormatter
import ru.grabovsky.poibot.strategy.flow.reviews.CommentsView
import ru.grabovsky.poibot.strategy.flow.reviews.RateView
import ru.grabovsky.poibot.strategy.flow.reviews.ReviewsFlow
import ru.grabovsky.poibot.strategy.flow.reviews.ReviewsState
import java.time.Instant
import java.util.*
import org.telegram.telegrambots.meta.api.objects.User as TgUser

class ReviewsFlowTest : ShouldSpec({
    val locale = Locale.forLanguageTag("ru")
    val reviews = mockk<ReviewService>()
    val savedPlaces = mockk<SavedPlaceService>()
    val i18n = KeyI18n()
    val formatter = PlaceFormatter(i18n)
    val links = mockk<PlaceLinkService> { every { candidatesFor(any(), any()) } returns emptyList() }
    val flow = ReviewsFlow(reviews, savedPlaces, formatter, PlaceCardFactory(i18n, formatter, reviews, links), i18n)
    val tgUser = mockk<TgUser> { every { id } returns 1L }
    val place = SavedPlace(id = 5L, ownerId = 1L, placeId = 77L, name = "Хмель")

    beforeTest {
        clearMocks(reviews, savedPlaces)
        every { savedPlaces.get(1L, 5L) } returns place
        every { reviews.summary(77L) } returns RatingSummary(4.0, 3)
        every { reviews.userRating(77L, 1L) } returns null
        every { reviews.commentCount(77L) } returns 0
        every { reviews.userComment(77L, 1L) } returns null
    }

    fun ctx(state: ReviewsState = ReviewsState()) =
        FlowContext(tgUser, locale, FlowStateHolder("comments", state, mapOf("main" to 1)))

    fun callback(messageId: Int? = 33): CallbackQuery = mockk {
        every { id } returns "cb"
        every { from } returns tgUser
        every { message } returns messageId?.let { id -> mockk<Message> { every { this@mockk.messageId } returns id } }
    }

    fun textMessage(text: String): Message = mockk {
        every { this@mockk.text } returns text
        every { from } returns tgUser
        every { messageId } returns 90
    }

    should("do nothing on start: the work is done by the following callback") {
        flow.start(FlowStartContext(tgUser, locale, "RATE:5")).actions shouldBe emptyList()
    }

    context("rating") {
        should("show five stars with the current average") {
            val result = flow.onCallback(ctx(), callback(), "RATE:5")

            result.shouldNotBeNull()
            val send = result.actions.filterIsInstance<SendMessageAction>().single()
            send.message.inlineButtons.map { it.payload.data } shouldBe
                    listOf("SET:5:1", "SET:5:2", "SET:5:3", "SET:5:4", "SET:5:5", "CLOSE")
            (send.message.model as RateView).rating shouldBe "4.0 (3)"
        }

        should("offer removal and mark the current rating") {
            every { reviews.userRating(77L, 1L) } returns 4

            val result = flow.onCallback(ctx(), callback(), "RATE:5")

            val buttons = result.shouldNotBeNull().actions.filterIsInstance<SendMessageAction>().single().message.inlineButtons
            buttons[3].text shouldBe "✅ 4 ⭐"
            buttons.map { it.payload.data }.takeLast(2) shouldBe listOf("UNSET:5", "CLOSE")
        }

        should("save the chosen rating, close the dialog and toast") {
            every { reviews.rate(1L, 5L, 4) } returns RateResult.OK

            val result = flow.onCallback(ctx(), callback(33), "SET:5:4")

            result.shouldNotBeNull().actions.shouldContain(DeleteMessageIdAction(33))
            result.actions.filterIsInstance<AnswerCallbackAction>().single().text shouldBe "alerts.reviews.rated"
        }

        should("not accept a foreign record or a value out of range") {
            every { reviews.rate(1L, 6L, 3) } returns RateResult.NOT_OWNER

            flow.onCallback(ctx(), callback(), "SET:5:9") shouldBe null
            val result = flow.onCallback(ctx(), callback(), "SET:6:3")
            result.shouldNotBeNull().actions.filterIsInstance<AnswerCallbackAction>().single().showAlert shouldBe true
        }

        should("remove the rating") {
            every { reviews.removeRating(1L, 5L) } returns true

            val result = flow.onCallback(ctx(), callback(33), "UNSET:5")

            result.shouldNotBeNull().actions.shouldContain(DeleteMessageIdAction(33))
            verify { reviews.removeRating(1L, 5L) }
        }
    }

    context("adding a comment") {
        should("ask for the text and remember the record") {
            val state = ReviewsState()

            val result = flow.onCallback(ctx(state), callback(), "ADDC:5")

            result.shouldNotBeNull()
            state.awaitingSavedId shouldBe 5L
            result.actions.filterIsInstance<SendMessageAction>().single().bindingKey shouldBe "prompt"
        }

        should("show the previous comment when editing and report the update") {
            every { reviews.userComment(77L, 1L) } returns CommentItem(8L, "старый", null, true)
            val state = ReviewsState()

            val prompt = flow.onCallback(ctx(state), callback(), "ADDC:5").shouldNotBeNull()
                .actions.filterIsInstance<SendMessageAction>().single()
            (prompt.message.model as ru.grabovsky.poibot.strategy.flow.reviews.ReviewPromptView).current shouldBe "старый"

            every { reviews.saveComment(1L, 5L, "новый") } returns AddCommentResult.UPDATED
            val result = flow.onMessage(ctx(state), textMessage("новый"))
            result.shouldNotBeNull()
            result.actions.filterIsInstance<SendMessageAction>().single().message.model.toString() shouldBe
                    "NoticeView(text=notices.comment.updated)"
            state.awaitingSavedId shouldBe null
        }

        should("explain that a comment hidden by reports cannot be edited") {
            val state = ReviewsState(awaitingSavedId = 5L)
            every { reviews.saveComment(1L, 5L, "правка") } returns AddCommentResult.HIDDEN

            val result = flow.onMessage(ctx(state), textMessage("правка"))

            result.shouldNotBeNull().actions.filterIsInstance<SendMessageAction>().single().message.model.toString() shouldBe
                    "NoticeView(text=alerts.reviews.hidden)"
        }

        should("ignore text when no comment is awaited") {
            flow.onMessage(ctx(), textMessage("привет")) shouldBe null
        }

        should("save the text, clean up the dialog and confirm with a temporary message") {
            val state = ReviewsState(awaitingSavedId = 5L)
            every { reviews.saveComment(1L, 5L, "Отличный бар") } returns AddCommentResult.ADDED

            val result = flow.onMessage(ctx(state), textMessage("Отличный бар"))

            result.shouldNotBeNull()
            state.awaitingSavedId shouldBe null
            result.actions.shouldContain(DeleteMessageIdAction(90))
            result.actions.shouldContain(DeleteMessageAction("prompt"))
            result.actions.filterIsInstance<SendMessageAction>().single().message.autoDeleteAfterSeconds shouldBe 6
        }

        should("keep waiting after a too short or too long text") {
            val state = ReviewsState(awaitingSavedId = 5L)
            every { reviews.saveComment(1L, 5L, "x") } returns AddCommentResult.TOO_SHORT

            val result = flow.onMessage(ctx(state), textMessage("x"))

            result.shouldNotBeNull()
            state.awaitingSavedId shouldBe 5L
            result.actions.filterIsInstance<DeleteMessageAction>().size shouldBe 0
        }

        should("stop waiting when the limit is reached") {
            val state = ReviewsState(awaitingSavedId = 5L)
            every { reviews.saveComment(1L, 5L, "ещё один") } returns AddCommentResult.HIDDEN

            flow.onMessage(ctx(state), textMessage("ещё один"))

            state.awaitingSavedId shouldBe null
        }

        should("cancel the prompt") {
            val state = ReviewsState(awaitingSavedId = 5L)

            val result = flow.onCallback(ctx(state), callback(), "PCANCEL")

            state.awaitingSavedId shouldBe null
            result.shouldNotBeNull().actions.shouldContain(DeleteMessageAction("prompt"))
        }
    }

    context("reading comments") {
        val now = Instant.parse("2026-10-12T10:00:00Z")
        val page = CommentsPage(
            listOf(CommentItem(10L, "Отлично", now, mine = true), CommentItem(11L, "Дорого", now, mine = false)),
            page = 0, totalPages = 2, total = 7,
        )

        beforeTest {
            every { reviews.comments(77L, 1L, any(), any()) } returns page
            every { reviews.commentCount(77L) } returns 7
        }

        should("show anonymous comments with delete for own and report for others") {
            val result = flow.onCallback(ctx(), callback(), "COM:77:0")

            val send = result.shouldNotBeNull().actions.filterIsInstance<SendMessageAction>().single()
            send.message.inlineButtons.map { it.payload.data } shouldBe
                    listOf("CDEL:10:77:0", "CREP:11:77:0", "CP:77:1", "CLOSE")
            val view = send.message.model as CommentsView
            view.lines.map { it.mine } shouldBe listOf(true, false)
            view.lines.first().date shouldBe "12.10.2026"
            view.rating shouldBe "4.0 (3)"
        }

        should("keep the close button in the last row of the comments list") {
            val send = flow.onCallback(ctx(), callback(), "COM:77:0").shouldNotBeNull()
                .actions.filterIsInstance<SendMessageAction>().single()

            val close = send.message.inlineButtons.single { it.payload.data == "CLOSE" }
            close.row shouldBe FlowInlineButton.LAST_ROW
            send.message.inlineButtons.filter { it !== close }.all { it.row < FlowInlineButton.LAST_ROW } shouldBe true
        }

        should("replace the message when paging") {
            val result = flow.onCallback(ctx(), callback(40), "CP:77:1")

            result.shouldNotBeNull().actions.shouldContain(DeleteMessageIdAction(40))
            result.actions.filterIsInstance<SendMessageAction>().single().bindingKey shouldBe "comments"
        }

        should("delete an own comment and refresh the list") {
            every { reviews.deleteComment(1L, 10L) } returns true

            val result = flow.onCallback(ctx(), callback(40), "CDEL:10:77:0")

            result.shouldNotBeNull()
            verify { reviews.deleteComment(1L, 10L) }
            result.actions.filterIsInstance<SendMessageAction>().size shouldBe 1
        }

        should("not show an empty list after the last comment is deleted") {
            every { reviews.deleteComment(1L, 10L) } returns true
            every { reviews.commentCount(77L) } returns 0

            val result = flow.onCallback(ctx(), callback(40), "CDEL:10:77:0")

            result.shouldNotBeNull().actions.shouldContain(DeleteMessageIdAction(40))
            result.actions.filterIsInstance<SendMessageAction>().size shouldBe 0
        }

        should("report a comment and tell the result") {
            every { reviews.report(1L, 11L) } returns ReportResult.REPORTED

            val result = flow.onCallback(ctx(), callback(40), "CREP:11:77:0")

            result.shouldNotBeNull().actions.filterIsInstance<AnswerCallbackAction>().single().text shouldBe
                    "alerts.reviews.reported"
        }
    }

    context("card refresh") {
        fun pressRate(state: ReviewsState, cardId: Int, context: String) =
            flow.onCallback(ctx(state), callback(cardId), "RATE:5:$context")

        should("redraw the card text after a rating is set") {
            every { reviews.rate(1L, 5L, 4) } returns RateResult.OK
            val state = ReviewsState()
            pressRate(state, 20, "P:0")

            val result = flow.onCallback(ctx(state), callback(33), "SET:5:4")

            val refresh = result.shouldNotBeNull().actions.filterIsInstance<EditCardAction>().single()
            refresh.messageId shouldBe 20
            refresh.caption shouldBe false
            result.actions.shouldContain(DeleteMessageIdAction(33))
        }

        should("redraw the caption for a card with a photo and keep the distance of a nearby card") {
            every { savedPlaces.get(1L, 5L) } returns place.copy(photoFileId = "photo")
            every { reviews.rate(1L, 5L, 5) } returns RateResult.OK
            val state = ReviewsState()
            pressRate(state, 21, "N:120")

            val result = flow.onCallback(ctx(state), callback(33), "SET:5:5")

            val refresh = result.shouldNotBeNull().actions.filterIsInstance<EditCardAction>().single()
            refresh.caption shouldBe true
            (refresh.message.model as PlaceCardView).distanceText shouldBe "120 unit.m"
            refresh.message.inlineButtons.map { it.payload.flow } shouldBe
                    listOf("ADD_PLACE", "NEARBY", "PUBLISH", "SHARED", "REVIEWS", "REVIEWS")
        }

        should("redraw the card after a comment is added or the rating removed") {
            val state = ReviewsState()
            flow.onCallback(ctx(state), callback(22), "ADDC:5:P:0")
            every { reviews.saveComment(1L, 5L, "Отличный бар") } returns AddCommentResult.ADDED

            val added = flow.onMessage(ctx(state), textMessage("Отличный бар"))
            added.shouldNotBeNull().actions.filterIsInstance<EditCardAction>().single().messageId shouldBe 22

            every { reviews.removeRating(1L, 5L) } returns true
            val removed = flow.onCallback(ctx(state), callback(40), "UNSET:5")
            removed.shouldNotBeNull().actions.filterIsInstance<EditCardAction>().single().messageId shouldBe 22
        }

        should("remember the card when comments are opened from it and refresh after deleting a comment") {
            val now = java.time.Instant.parse("2026-10-12T10:00:00Z")
            every { reviews.comments(77L, 1L, any(), any()) } returns
                    CommentsPage(listOf(CommentItem(10L, "Отлично", now, true)), 0, 1, 1)
            every { reviews.deleteComment(1L, 10L) } returns true
            val state = ReviewsState()
            flow.onCallback(ctx(state), callback(23), "COM:77:0:5:P:0")

            val result = flow.onCallback(ctx(state), callback(40), "CDEL:10:77:0")

            result.shouldNotBeNull().actions.filterIsInstance<EditCardAction>().single().messageId shouldBe 23
        }

        should("not redraw anything when the dialog did not start from a card") {
            every { reviews.rate(1L, 5L, 4) } returns RateResult.OK

            val result = flow.onCallback(ctx(), callback(33), "SET:5:4")

            result.shouldNotBeNull().actions.filterIsInstance<EditCardAction>().size shouldBe 0
        }
    }
})
