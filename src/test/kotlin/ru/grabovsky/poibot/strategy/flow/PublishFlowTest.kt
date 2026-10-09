package ru.grabovsky.poibot.strategy.flow

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.ChatShared
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.entity.Chat
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.service.interfaces.*
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.publish.GroupDto
import ru.grabovsky.poibot.strategy.flow.publish.PublishFlow
import ru.grabovsky.poibot.strategy.flow.publish.PublishState
import java.util.*
import org.telegram.telegrambots.meta.api.objects.User as TgUser

class PublishFlowTest : ShouldSpec({
    val locale = Locale.forLanguageTag("ru")
    val tgUser = mockk<TgUser> { every { id } returns 7L }
    val savedPlaceService = mockk<SavedPlaceService>()
    val publishService = mockk<PublishService>()
    val flow = PublishFlow(savedPlaceService, publishService, KeyI18n())
    val groupA = Chat(id = -1L, type = "group", title = "Friends")
    val groupB = Chat(id = -2L, type = "supergroup", title = "Work")

    beforeTest {
        clearMocks(savedPlaceService, publishService)
        every { publishService.getUserGroups(7L) } returns listOf(groupA, groupB)
        every { publishService.publishedCounts(any(), any()) } returns emptyMap()
        every { savedPlaceService.get(7L, any()) } answers {
            SavedPlace(id = secondArg(), ownerId = 7L, placeId = 1L, name = "Place ${secondArg<Long>()}")
        }
        every { savedPlaceService.list(7L, any(), any()) } returns SavedPlacePage(
            listOf(
                SavedPlace(id = 1L, ownerId = 7L, placeId = 1L, name = "A"),
                SavedPlace(id = 2L, ownerId = 7L, placeId = 1L, name = "B"),
            ),
            0, 1, 2,
        )
    }

    fun ctx(state: PublishState) =
        FlowContext(tgUser, locale, FlowStateHolder("select", state, mapOf("main" to 1, "pick" to 2)))

    fun callback(): CallbackQuery = mockk {
        every { id } returns "cb"
        every { from } returns tgUser
    }

    fun groupState(vararg ids: Long, single: Boolean = false) = PublishState(
        mode = PublishState.MODE_GROUPS,
        single = single,
        selected = ids.toMutableList(),
        groups = mutableListOf(GroupDto(-1L, "Friends"), GroupDto(-2L, "Work")),
    )

    should("open the groups screen with a group picker for a single place") {
        val result = flow.start(FlowStartContext(tgUser, locale, "ONE:5"))

        result.payload.mode shouldBe PublishState.MODE_GROUPS
        result.payload.selected shouldBe listOf(5L)
        result.payload.single shouldBe true
        val sends = result.actions.filterIsInstance<SendMessageAction>()
        sends.map { it.bindingKey } shouldBe listOf("main", "pick")
        sends[0].message.inlineButtons.map { it.payload.data } shouldBe listOf("G:-1", "G:-2", "DONE")
        sends[1].message.replyButtons.single().requestChatId shouldBe "1"
    }

    should("start with place selection when no place is given") {
        val result = flow.start(FlowStartContext(tgUser, locale))

        result.payload.mode shouldBe PublishState.MODE_SELECT
        val send = result.actions.single().shouldBeInstanceOf<SendMessageAction>()
        send.message.inlineButtons.map { it.payload.data } shouldBe listOf("S:1", "S:2", "NEXT", "CANCEL")
    }

    should("toggle place selection") {
        val state = PublishState()

        flow.onCallback(ctx(state), callback(), "S:1")
        state.selected shouldBe listOf(1L)

        flow.onCallback(ctx(state), callback(), "S:1")
        state.selected shouldBe emptyList()
    }

    should("not continue without selected places") {
        val result = flow.onCallback(ctx(PublishState()), callback(), "NEXT")

        result.shouldNotBeNull()
        result.actions.filterIsInstance<AnswerCallbackAction>().single().text shouldBe "alerts.publish.nothing_selected"
    }

    should("move to groups with the selected places and send the picker") {
        val state = PublishState(selected = mutableListOf(1L, 2L))

        val result = flow.onCallback(ctx(state), callback(), "NEXT")

        result.shouldNotBeNull()
        state.mode shouldBe PublishState.MODE_GROUPS
        result.actions.filterIsInstance<SendMessageAction>().single().bindingKey shouldBe "pick"
    }

    should("publish to a group where nothing is published yet") {
        val state = groupState(1L, 2L)
        every { publishService.setPublished(7L, listOf(1L, 2L), -1L, true) } returns 2

        val result = flow.onCallback(ctx(state), callback(), "G:-1")

        result.shouldNotBeNull()
        verify { publishService.setPublished(7L, listOf(1L, 2L), -1L, true) }
    }

    should("unpublish when all places are already published in the group") {
        val state = groupState(1L, 2L)
        every { publishService.publishedCounts(listOf(1L, 2L), listOf(-1L)) } returns mapOf(-1L to 2)
        every { publishService.setPublished(7L, listOf(1L, 2L), -1L, false) } returns 2

        flow.onCallback(ctx(state), callback(), "G:-1")

        verify { publishService.setPublished(7L, listOf(1L, 2L), -1L, false) }
    }

    should("publish the rest when only part of the places is published") {
        val state = groupState(1L, 2L)
        every { publishService.publishedCounts(listOf(1L, 2L), listOf(-1L)) } returns mapOf(-1L to 1)
        every { publishService.setPublished(7L, listOf(1L, 2L), -1L, true) } returns 1

        flow.onCallback(ctx(state), callback(), "G:-1")

        verify { publishService.setPublished(7L, listOf(1L, 2L), -1L, true) }
    }

    should("show an alert when publishing is not allowed") {
        val state = groupState(1L)
        every { publishService.setPublished(7L, listOf(1L), -1L, true) } throws PublishNotAllowedException("no")

        val result = flow.onCallback(ctx(state), callback(), "G:-1")

        result.shouldNotBeNull()
        result.actions.filterIsInstance<AnswerCallbackAction>().single().showAlert shouldBe true
    }

    should("ignore groups that are not in the list") {
        val result = flow.onCallback(ctx(groupState(1L)), callback(), "G:-999")

        result.shouldNotBeNull()
        result.actions.filterIsInstance<AnswerCallbackAction>().single().showAlert shouldBe true
        verify(exactly = 0) { publishService.setPublished(any(), any(), any(), any()) }
    }

    should("finish with a message that removes the reply keyboard") {
        val result = flow.onCallback(ctx(groupState(1L)), callback(), "DONE")

        result.shouldNotBeNull()
        result.completed shouldBe true
        val done = result.actions.filterIsInstance<SendMessageAction>().single()
        done.message.removeReplyKeyboard shouldBe true
        result.actions.shouldContain(DeleteMessageAction("pick"))
    }

    should("refresh the groups list when the user shares a chat") {
        val state = groupState(1L)
        val shared = mockk<ChatShared> { every { chatId } returns -3L }
        val message = mockk<Message> {
            every { chatShared } returns shared
            every { from } returns tgUser
            every { messageId } returns 99
        }

        val result = flow.onMessage(ctx(state), message)

        result.shouldNotBeNull()
        result.actions.shouldContain(DeleteMessageIdAction(99))
        result.actions.filterIsInstance<EditMessageAction>().single().bindingKey shouldBe "main"
        verify { publishService.getUserGroups(7L) }
    }

    should("ignore regular messages") {
        val message = mockk<Message> { every { chatShared } returns null }

        flow.onMessage(ctx(groupState(1L)), message) shouldBe null
    }

    should("ignore chat_shared before the groups screen") {
        val shared = mockk<ChatShared>()
        val message = mockk<Message> { every { chatShared } returns shared }

        flow.onMessage(ctx(PublishState()), message) shouldBe null
    }
})
