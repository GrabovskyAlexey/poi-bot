package ru.grabovsky.poibot.service

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.grabovsky.poibot.service.interfaces.ChatCleanupPolicy
import ru.grabovsky.poibot.service.interfaces.I18nService
import ru.grabovsky.poibot.service.interfaces.UserService
import java.util.*
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.telegram.telegrambots.meta.api.objects.User as TgUser

class UnknownCommandServiceImplTest : ShouldSpec({
    val client = mockk<TelegramClient>()
    val userService = mockk<UserService>()
    val scheduler = mockk<ScheduledExecutorService>(relaxed = true)
    val i18n = object : I18nService {
        override fun i18n(code: String, locale: Locale, default: String?, vararg args: Any?) = "$code/${locale.language}"
    }
    val cleanup = mockk<ChatCleanupPolicy>()
    val service = UnknownCommandServiceImpl(client, userService, i18n, cleanup, scheduler)

    fun command(userChat: Boolean = true): Message = mockk {
        every { chat } returns mockk<Chat> { every { isUserChat } returns userChat }
        every { chatId } returns 55L
        every { messageId } returns 10
        every { from } returns mockk<TgUser> { every { id } returns 7L }
    }

    beforeTest {
        clearMocks(client, userService, scheduler)
        every { userService.getUser(7L) } returns null
        every { cleanup.enabled(55L) } returns true
    }

    should("reply in a private chat and delete both messages later") {
        val sent = slot<SendMessage>()
        every { client.execute(capture(sent)) } returns mockk<Message> { every { messageId } returns 11 }
        val task = slot<Runnable>()
        every { scheduler.schedule(capture(task), 5L, TimeUnit.SECONDS) } returns null
        every { client.execute(any<DeleteMessages>()) } returns true

        service.reply(command())

        sent.captured.chatId shouldBe "55"
        sent.captured.text shouldBe "alerts.command.unknown/ru"
        task.captured.run()
        verify { client.execute(match<DeleteMessages> { it.messageIds == listOf(10, 11) && it.chatId == "55" }) }
    }

    should("keep both messages when auto-clean is off") {
        every { cleanup.enabled(55L) } returns false
        val sent = slot<SendMessage>()
        every { client.execute(capture(sent)) } returns mockk<Message> { every { messageId } returns 11 }

        service.reply(command())

        sent.captured.text shouldBe "alerts.command.unknown/ru"
        verify(exactly = 0) { scheduler.schedule(any<Runnable>(), any(), any()) }
    }

    should("stay silent in groups") {
        service.reply(command(userChat = false))

        verify(exactly = 0) { client.execute(any<SendMessage>()) }
        verify(exactly = 0) { scheduler.schedule(any<Runnable>(), any(), any()) }
    }

    should("still remove the command when the reply could not be sent") {
        every { client.execute(any<SendMessage>()) } throws RuntimeException("boom")
        val task = slot<Runnable>()
        every { scheduler.schedule(capture(task), 5L, TimeUnit.SECONDS) } returns null
        every { client.execute(any<DeleteMessages>()) } returns true

        service.reply(command())
        task.captured.run()

        verify { client.execute(match<DeleteMessages> { it.messageIds == listOf(10) }) }
    }
})
