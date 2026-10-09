package ru.grabovsky.poibot.strategy.commands

import io.kotest.core.spec.style.ShouldSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.grabovsky.poibot.entity.User
import ru.grabovsky.poibot.entity.UserProfile
import ru.grabovsky.poibot.service.interfaces.ChatService
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowEngine
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKeys
import org.telegram.telegrambots.meta.api.objects.User as TgUser

class CommandsTest : ShouldSpec({
    val telegramClient = mockk<TelegramClient>(relaxed = true)
    val chat = mockk<Chat>(relaxed = true) { every { isUserChat } returns true }
    val groupChat = mockk<Chat>(relaxed = true) {
        every { isUserChat } returns false
        every { id } returns -1001L
    }

    should("create or update user and start flow for StartCommand") {
        val userService = mockk<UserService>()
        val engine = mockk<FlowEngine>(relaxed = true)
        val chatService = mockk<ChatService>(relaxed = true)
        val command = StartCommand(userService, engine, chatService)
        val tgUser = mockk<TgUser>(relaxed = true) { every { id } returns 100L }
        val persisted = User(100L, "Tester", null, "tester").apply { profile = UserProfile(userId = userId, user = this) }
        every { userService.createOrUpdateUser(tgUser) } returns persisted
        every { userService.getUser(100L) } returns persisted
        every { engine.start(FlowKeys.START, tgUser, any()) } returns true

        command.execute(telegramClient, tgUser, chat, emptyArray())

        verify { userService.createOrUpdateUser(tgUser) }
        verify { engine.start(FlowKeys.START, tgUser, any()) }
    }

    should("start help flow when executing HelpCommand") {
        val userService = mockk<UserService>()
        val engine = mockk<FlowEngine>(relaxed = true)
        val chatService = mockk<ChatService>(relaxed = true)
        val command = HelpCommand(userService, engine, chatService)
        val tgUser = mockk<TgUser>(relaxed = true) { every { id } returns 150L }
        val persisted = User(150L, "Help", null, "help").apply { profile = UserProfile(userId = userId, user = this) }
        every { userService.createOrUpdateUser(tgUser) } returns persisted
        every { userService.getUser(150L) } returns persisted
        every { engine.start(FlowKeys.HELP, tgUser, any()) } returns true

        command.execute(telegramClient, tgUser, chat, emptyArray())

        verify { engine.start(FlowKeys.HELP, tgUser, any()) }
    }

    should("only register chat and link user when command is used in a group") {
        val userService = mockk<UserService>(relaxed = true)
        val engine = mockk<FlowEngine>()
        val chatService = mockk<ChatService>(relaxed = true)
        val command = StartCommand(userService, engine, chatService)
        val tgUser = mockk<TgUser>(relaxed = true) { every { id } returns 300L }

        command.execute(telegramClient, tgUser, groupChat, emptyArray())

        verify { chatService.registerChat(groupChat) }
        verify { chatService.linkUser(300L, -1001L) }
        verify(exactly = 0) { engine.start(any(), any(), any()) }
    }
})
