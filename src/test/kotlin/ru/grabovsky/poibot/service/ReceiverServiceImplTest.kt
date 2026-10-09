package ru.grabovsky.poibot.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.ShouldSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.entity.FlowState
import ru.grabovsky.poibot.service.interfaces.FlowStateService
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowCallbackPayload
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowEngine
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKey
import org.telegram.telegrambots.meta.api.objects.User as TgUser
import ru.grabovsky.poibot.entity.User as BotUser

class ReceiverServiceImplTest : ShouldSpec({

    val userService = mockk<UserService>()
    val flowEngine = mockk<FlowEngine>()
    val flowStateService = mockk<FlowStateService>()
    val objectMapper = ObjectMapper().findAndRegisterModules()
    val service = ReceiverServiceImpl(userService, objectMapper, flowEngine, flowStateService)

    beforeTest {
        clearMocks(userService, flowEngine, flowStateService)
    }

    should("передавать сообщения активному флоу") {
        val telegramUser = mockk<TgUser>(relaxed = true) {
            every { id } returns 101L
        }
        val telegramMessage = mockk<Message> {
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
        verify(exactly = 0) { flowEngine.start(any(), any(), any()) }
    }

    should("перезапускать флоу, если callback не обработан с первого раза") {
        val telegramUser = mockk<TgUser>(relaxed = true) {
            every { id } returns 404L
        }
        val payload = FlowCallbackPayload(flow = "FLOW", data = "PAYLOAD")
        val callbackQueryMock = mockk<CallbackQuery> {
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
        every { flowEngine.start(any(), any(), any()) } returns true

        service.execute(update)

        verify(exactly = 2) {
            flowEngine.onCallback(FlowKey("FLOW"), telegramUser, any(), callbackQueryMock, "PAYLOAD")
        }
        verify {
            flowEngine.start(FlowKey("FLOW"), telegramUser, any())
        }
    }

    should("игнорировать некорректный callback payload") {
        val telegramUser = mockk<TgUser>(relaxed = true) {
            every { id } returns 505L
        }
        val callbackQueryMock = mockk<CallbackQuery> {
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
        verify(exactly = 0) { flowEngine.start(any(), any(), any()) }
    }
})
