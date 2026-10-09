package ru.grabovsky.poibot.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.ShouldSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.ChatShared
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberUpdated
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.entity.FlowState
import ru.grabovsky.poibot.service.interfaces.ChatService
import ru.grabovsky.poibot.service.interfaces.FlowStateService
import ru.grabovsky.poibot.service.interfaces.GroupPlacesService
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowCallbackPayload
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowEngine
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKey
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKeys
import org.telegram.telegrambots.meta.api.objects.User as TgUser
import ru.grabovsky.poibot.entity.User as BotUser

class ReceiverServiceImplTest : ShouldSpec({

    val userService = mockk<UserService>()
    val flowEngine = mockk<FlowEngine>()
    val flowStateService = mockk<FlowStateService>()
    val chatService = mockk<ChatService>(relaxed = true)
    val groupPlacesService = mockk<GroupPlacesService>(relaxed = true)
    val privateChat = mockk<Chat> { every { isUserChat } returns true }
    val objectMapper = ObjectMapper().findAndRegisterModules()
    val service = ReceiverServiceImpl(userService, objectMapper, flowEngine, flowStateService, chatService, groupPlacesService)

    beforeTest {
        clearMocks(userService, flowEngine, flowStateService, chatService, groupPlacesService)
    }

    should("запускать поиск рядом для геопозиции без активного флоу") {
        val telegramUser = mockk<TgUser>(relaxed = true) { every { id } returns 102L }
        val telegramMessage = mockk<Message>(relaxed = true) {
            every { chat } returns privateChat
            every { chatShared } returns null
            every { from } returns telegramUser
            every { location } returns mockk<org.telegram.telegrambots.meta.api.objects.location.Location> {
                every { latitude } returns 55.5
                every { longitude } returns 37.5
            }
        }
        val update = mockk<Update> {
            every { hasCallbackQuery() } returns false
            every { hasMessage() } returns true
            every { message } returns telegramMessage
        }
        every { userService.createOrUpdateUser(telegramUser) } returns BotUser(102L, null, null, null)
        every { userService.getUser(102L) } returns null
        every { flowStateService.findListFlow(102L) } returns null
        every { flowEngine.start(any(), any(), any(), any()) } returns true

        service.execute(update)

        verify { flowEngine.start(FlowKeys.NEARBY, telegramUser, any(), "LOC:55.5:37.5") }
    }

    should("передавать сообщения активному флоу") {
        val telegramUser = mockk<TgUser>(relaxed = true) {
            every { id } returns 101L
        }
        val telegramMessage = mockk<Message> {
            every { chat } returns privateChat
            every { chatShared } returns null
            every { from } returns telegramUser
            every { messageId } returns 41
        }
        val update = mockk<Update> {
            every { hasCallbackQuery() } returns false
            every { hasMessage() } returns true
            every { message } returns telegramMessage
        }
        every { userService.createOrUpdateUser(telegramUser) } returns BotUser(
            userId = 101L,
            firstName = null,
            lastName = null,
            userName = null,
        )
        every { userService.getUser(101L) } returns null
        every { flowStateService.findListFlow(101L) } returns FlowState(
            id = 1L,
            userId = 101L,
            flowKey = "TEST",
            stepKey = "STEP",
        )
        every { flowEngine.onMessage(any(), any(), any(), any()) } returns true

        service.execute(update)

        verify {
            flowEngine.onMessage(FlowKey("TEST"), telegramUser, any(), telegramMessage)
        }
    }

    should("игнорировать сообщения без активного флоу") {
        val telegramUser = mockk<TgUser>(relaxed = true) {
            every { id } returns 202L
        }
        val telegramMessage = mockk<Message> {
            every { chat } returns privateChat
            every { chatShared } returns null
            every { from } returns telegramUser
        }
        val update = mockk<Update> {
            every { hasCallbackQuery() } returns false
            every { hasMessage() } returns true
            every { message } returns telegramMessage
        }
        every { userService.createOrUpdateUser(telegramUser) } returns BotUser(
            userId = 202L,
            firstName = null,
            lastName = null,
            userName = null,
        )
        every { flowStateService.findListFlow(202L) } returns null

        service.execute(update)

        verify(exactly = 0) { flowEngine.onMessage(any(), any(), any(), any()) }
    }

    should("обрабатывать callback через флоу-полезную нагрузку") {
        val telegramUser = mockk<TgUser>(relaxed = true) {
            every { id } returns 303L
        }
        val payload = FlowCallbackPayload(flow = "FLOW", data = "DATA")
        val callbackQueryMock = mockk<CallbackQuery> {
            every { message } returns null
            every { from } returns telegramUser
            every { data } returns objectMapper.writeValueAsString(payload)
        }
        val update = mockk<Update> {
            every { hasCallbackQuery() } returns true
            every { hasMessage() } returns false
            every { callbackQuery } returns callbackQueryMock
        }
        every { userService.createOrUpdateUser(telegramUser) } returns BotUser(
            userId = 303L,
            firstName = null,
            lastName = null,
            userName = null,
        )
        every { userService.getUser(303L) } returns null
        every { flowEngine.onCallback(any(), any(), any(), any(), any()) } returns true

        service.execute(update)

        verify {
            flowEngine.onCallback(FlowKey("FLOW"), telegramUser, any(), callbackQueryMock, "DATA")
        }
        verify(exactly = 0) { flowEngine.start(any(), any(), any(), any()) }
    }

    should("перезапускать флоу, если callback не обработан с первого раза") {
        val telegramUser = mockk<TgUser>(relaxed = true) {
            every { id } returns 404L
        }
        val payload = FlowCallbackPayload(flow = "FLOW", data = "PAYLOAD")
        val callbackQueryMock = mockk<CallbackQuery> {
            every { message } returns null
            every { from } returns telegramUser
            every { data } returns objectMapper.writeValueAsString(payload)
        }
        val update = mockk<Update> {
            every { hasCallbackQuery() } returns true
            every { hasMessage() } returns false
            every { callbackQuery } returns callbackQueryMock
        }
        every { userService.createOrUpdateUser(telegramUser) } returns BotUser(
            userId = 404L,
            firstName = null,
            lastName = null,
            userName = null,
        )
        every { userService.getUser(404L) } returns null
        every { flowEngine.onCallback(any(), any(), any(), any(), any()) } returnsMany listOf(false, true)
        every { flowEngine.start(any(), any(), any(), any()) } returns true

        service.execute(update)

        verify(exactly = 2) {
            flowEngine.onCallback(FlowKey("FLOW"), telegramUser, any(), callbackQueryMock, "PAYLOAD")
        }
        verify {
            flowEngine.start(FlowKey("FLOW"), telegramUser, any(), any())
        }
    }

    should("игнорировать некорректный callback payload") {
        val telegramUser = mockk<TgUser>(relaxed = true) {
            every { id } returns 505L
        }
        val callbackQueryMock = mockk<CallbackQuery> {
            every { message } returns null
            every { from } returns telegramUser
            every { data } returns "broken"
        }
        val update = mockk<Update> {
            every { hasCallbackQuery() } returns true
            every { hasMessage() } returns false
            every { callbackQuery } returns callbackQueryMock
        }
        every { userService.createOrUpdateUser(telegramUser) } returns BotUser(
            userId = 505L,
            firstName = null,
            lastName = null,
            userName = null,
        )

        service.execute(update)

        verify(exactly = 0) { flowEngine.onCallback(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { flowEngine.start(any(), any(), any(), any()) }
    }

    should("register shared chat and link user on chat_shared") {
        val telegramUser = mockk<TgUser>(relaxed = true) { every { id } returns 401L }
        val shared = mockk<ChatShared> {
            every { chatId } returns -1002L
            every { title } returns "Friends"
        }
        val telegramMessage = mockk<Message> {
            every { chat } returns privateChat
            every { chatShared } returns shared
            every { from } returns telegramUser
            every { messageId } returns 70
        }
        val update = mockk<Update> {
            every { hasCallbackQuery() } returns false
            every { hasMessage() } returns true
            every { message } returns telegramMessage
        }
        every { userService.createOrUpdateUser(telegramUser) } returns mockk(relaxed = true)
        every { flowStateService.findListFlow(401L) } returns null

        service.execute(update)

        verify { chatService.registerSharedChat(-1002L, "Friends") }
        verify { chatService.linkUser(401L, -1002L) }
        verify(exactly = 0) { flowEngine.onMessage(any(), any(), any(), any()) }
    }

    should("migrate chat id when group becomes supergroup") {
        val groupChat = mockk<Chat> {
            every { isUserChat } returns false
            every { id } returns -5L
            every { type } returns "group"
        }
        val telegramMessage = mockk<Message> {
            every { chat } returns groupChat
            every { migrateToChatId } returns -1005L
            every { messageId } returns 71
        }
        val update = mockk<Update> {
            every { hasCallbackQuery() } returns false
            every { hasMessage() } returns true
            every { message } returns telegramMessage
        }

        service.execute(update)

        verify { chatService.migrate(-5L, -1005L) }
    }

    should("activate group and link the user who added the bot") {
        val adder = mockk<TgUser>(relaxed = true) { every { id } returns 402L }
        val groupChat = mockk<Chat> {
            every { isUserChat } returns false
            every { isChannelChat } returns false
            every { id } returns -7L
            every { type } returns "supergroup"
        }
        val newMember = mockk<ChatMember> { every { status } returns "member" }
        val memberUpdate = mockk<ChatMemberUpdated> {
            every { chat } returns groupChat
            every { newChatMember } returns newMember
            every { from } returns adder
        }
        val update = mockk<Update> {
            every { hasCallbackQuery() } returns false
            every { hasMessage() } returns false
            every { hasMyChatMember() } returns true
            every { myChatMember } returns memberUpdate
        }
        every { userService.createOrUpdateUser(adder) } returns mockk(relaxed = true)

        service.execute(update)

        verify { chatService.setActive(-7L, true) }
        verify { chatService.linkUser(402L, -7L) }
    }

    should("deactivate group when bot is kicked") {
        val groupChat = mockk<Chat> {
            every { isUserChat } returns false
            every { isChannelChat } returns false
            every { id } returns -8L
            every { type } returns "group"
        }
        val newMember = mockk<ChatMember> { every { status } returns "kicked" }
        val memberUpdate = mockk<ChatMemberUpdated> {
            every { chat } returns groupChat
            every { newChatMember } returns newMember
        }
        val update = mockk<Update> {
            every { hasCallbackQuery() } returns false
            every { hasMessage() } returns false
            every { hasMyChatMember() } returns true
            every { myChatMember } returns memberUpdate
        }

        service.execute(update)

        verify { chatService.setActive(-8L, false) }
        verify(exactly = 0) { chatService.linkUser(any(), any()) }
    }

    should("hand a nearby location reply from a group to the group service") {
        val groupChat = mockk<Chat> {
            every { isUserChat } returns false
            every { id } returns -9L
            every { type } returns "supergroup"
        }
        val telegramMessage = mockk<Message> {
            every { chat } returns groupChat
            every { migrateToChatId } returns null
            every { messageId } returns 80
        }
        every { groupPlacesService.handleMessage(telegramMessage) } returns true
        val update = mockk<Update> {
            every { hasCallbackQuery() } returns false
            every { hasMessage() } returns true
            every { message } returns telegramMessage
        }

        service.execute(update)

        verify { groupPlacesService.handleMessage(telegramMessage) }
        verify(exactly = 0) { flowEngine.onMessage(any(), any(), any(), any()) }
    }

    should("route group callbacks to the group service and never start a flow") {
        val groupChat = mockk<Chat> {
            every { isUserChat } returns false
            every { id } returns -9L
        }
        val telegramUser = mockk<TgUser>(relaxed = true) { every { id } returns 501L }
        val callbackMessage = mockk<Message> { every { chat } returns groupChat }
        val callbackQueryMock = mockk<CallbackQuery> {
            every { from } returns telegramUser
            every { message } returns callbackMessage
            every { data } returns "{\"flow\":\"GP\",\"data\":\"P:1\"}"
            every { id } returns "cb"
        }
        val update = mockk<Update> {
            every { hasCallbackQuery() } returns true
            every { callbackQuery } returns callbackQueryMock
        }
        every { userService.createOrUpdateUser(telegramUser) } returns mockk(relaxed = true)

        service.execute(update)

        verify { groupPlacesService.onCallback(callbackQueryMock, "P:1") }
        verify(exactly = 0) { flowEngine.start(any(), any(), any(), any()) }
        verify { chatService.linkUser(501L, -9L) }
    }

    should("pass chat_shared to the active flow after linking the chat") {
        val telegramUser = mockk<TgUser>(relaxed = true) { every { id } returns 403L }
        val shared = mockk<ChatShared> {
            every { chatId } returns -1003L
            every { title } returns "Work"
        }
        val telegramMessage = mockk<Message> {
            every { chat } returns privateChat
            every { chatShared } returns shared
            every { from } returns telegramUser
            every { messageId } returns 71
        }
        val update = mockk<Update> {
            every { hasCallbackQuery() } returns false
            every { hasMessage() } returns true
            every { message } returns telegramMessage
        }
        every { userService.createOrUpdateUser(telegramUser) } returns mockk(relaxed = true)
        every { userService.getUser(403L) } returns null
        every { flowStateService.findListFlow(403L) } returns FlowState(userId = 403L, flowKey = "PUBLISH", stepKey = "groups")
        every { flowEngine.onMessage(FlowKey("PUBLISH"), telegramUser, any(), telegramMessage) } returns true

        service.execute(update)

        verify { chatService.registerSharedChat(-1003L, "Work") }
        verify { chatService.linkUser(403L, -1003L) }
        verify { flowEngine.onMessage(FlowKey("PUBLISH"), telegramUser, any(), telegramMessage) }
    }
})
