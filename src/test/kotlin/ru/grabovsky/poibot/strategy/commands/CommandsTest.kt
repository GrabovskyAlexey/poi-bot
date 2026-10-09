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
import ru.grabovsky.poibot.service.interfaces.GroupPlacesService
import org.telegram.telegrambots.meta.api.objects.message.Message
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

    should("delete the command message in a private chat after processing") {
        val userService = mockk<UserService>(relaxed = true)
        val engine = mockk<FlowEngine>(relaxed = true)
        val command = HelpCommand(userService, engine, mockk(relaxed = true))
        val tgUser = mockk<TgUser>(relaxed = true) { every { id } returns 160L }
        val message = mockk<Message>(relaxed = true) {
            every { this@mockk.chat } returns chat
            every { from } returns tgUser
            every { messageId } returns 5
            every { chatId } returns 160L
        }

        command.processMessage(telegramClient, message, emptyArray())

        verify { telegramClient.execute(match<org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage> { it.messageId == 5 }) }
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

    should("delegate places command in a group to the group service") {
        val userService = mockk<UserService>(relaxed = true)
        val engine = mockk<FlowEngine>()
        val chatService = mockk<ChatService>(relaxed = true)
        val groupService = mockk<GroupPlacesService>(relaxed = true)
        val command = PlacesCommand(userService, engine, chatService, groupService)
        val tgUser = mockk<TgUser>(relaxed = true) { every { id } returns 310L }
        val message = mockk<Message> {
            every { this@mockk.chat } returns groupChat
            every { from } returns tgUser
            every { messageId } returns 123
        }

        command.processMessage(telegramClient, message, emptyArray())

        verify { groupService.showPlaces(groupChat, tgUser, 123) }
        verify(exactly = 0) { engine.start(any(), any(), any(), any()) }
    }

    should("ask for location in a group for nearby and show add hint for add") {
        val groupService = mockk<GroupPlacesService>(relaxed = true)
        val engine = mockk<FlowEngine>()
        val tgUser = mockk<TgUser>(relaxed = true) { every { id } returns 311L }
        val message = mockk<Message> {
            every { this@mockk.chat } returns groupChat
            every { from } returns tgUser
            every { messageId } returns 124
        }

        NearbyCommand(mockk(relaxed = true), engine, mockk(relaxed = true), groupService)
            .processMessage(telegramClient, message, emptyArray())
        AddCommand(mockk(relaxed = true), engine, mockk(relaxed = true), groupService)
            .processMessage(telegramClient, message, emptyArray())

        verify { groupService.askNearbyLocation(groupChat, tgUser, 124) }
        verify { groupService.showAddHint(groupChat, tgUser, 124) }
    }

    should("start the shared flow with the token for a start deep link") {
        val userService = mockk<UserService>()
        val engine = mockk<FlowEngine>(relaxed = true)
        val command = StartCommand(userService, engine, mockk(relaxed = true))
        val tgUser = mockk<TgUser>(relaxed = true) { every { id } returns 320L }
        val persisted = User(320L, "Linked", null, "linked").apply { profile = UserProfile(userId = userId, user = this) }
        every { userService.createOrUpdateUser(tgUser) } returns persisted
        every { userService.getUser(320L) } returns persisted
        every { engine.start(any(), any(), any(), any()) } returns true

        command.execute(telegramClient, tgUser, chat, arrayOf("sp_abcdefghijklmnop"))

        verify { engine.start(FlowKeys.SHARED, tgUser, any(), "sp_abcdefghijklmnop") }
    }

    should("start the regular flow for unknown start payload") {
        val userService = mockk<UserService>()
        val engine = mockk<FlowEngine>(relaxed = true)
        val command = StartCommand(userService, engine, mockk(relaxed = true))
        val tgUser = mockk<TgUser>(relaxed = true) { every { id } returns 321L }
        val persisted = User(321L, "Plain", null, "plain").apply { profile = UserProfile(userId = userId, user = this) }
        every { userService.createOrUpdateUser(tgUser) } returns persisted
        every { userService.getUser(321L) } returns persisted
        every { engine.start(any(), any(), any(), any()) } returns true

        command.execute(telegramClient, tgUser, chat, arrayOf("something"))

        verify { engine.start(FlowKeys.START, tgUser, any(), null) }
    }
})
