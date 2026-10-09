package ru.grabovsky.poibot.strategy.flow

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.core.telegram.TelegramFlowActionExecutor
import ru.grabovsky.poibot.strategy.flow.core.templating.FlowTemplateRenderer
import org.telegram.telegrambots.meta.api.objects.User as TgUser
import java.util.Locale

/** Telegram-клиент валидирует запрос до отправки: reply-кнопки должны проходить validate(). */
class ReplyKeyboardValidationTest : ShouldSpec({
    val client = mockk<TelegramClient>()
    val renderer = mockk<FlowTemplateRenderer> { every { render(any(), any(), any(), any()) } returns "text" }
    val executor = TelegramFlowActionExecutor(client, ObjectMapper(), renderer)
    val user = mockk<TgUser> { every { id } returns 7L }
    val sent = slot<SendMessage>()

    fun send(button: FlowReplyButton): SendMessage {
        every { client.execute(capture(sent)) } returns mockk<Message> { every { messageId } returns 1 }
        val message = FlowMessage(FlowKeys.PUBLISH, "pick", replyButtons = listOf(button))
        executor.execute(user, Locale.ROOT, emptyMap(), listOf(SendMessageAction("pick", message)))
        return sent.captured
    }

    should("build a chat picker button that passes client-side validation") {
        val request = send(FlowReplyButton("pick", requestChatId = "1"))

        request.validate()
        val button = (request.replyMarkup as ReplyKeyboardMarkup).keyboard.single().single()
        button.requestChat.requestId shouldBe "1"
        button.requestLocation shouldBe null
    }

    should("build a location button that passes client-side validation") {
        val request = send(FlowReplyButton("loc", requestLocation = true))

        request.validate()
        (request.replyMarkup as ReplyKeyboardMarkup).keyboard.single().single().requestLocation shouldBe true
    }

    should("build a plain text button that passes client-side validation") {
        send(FlowReplyButton("plain")).validate()
    }

    should("keep the new binding when the old message with the same key is deleted in the same batch") {
        every { client.execute(any<org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages>()) } returns true
        every { client.execute(any<SendMessage>()) } returns mockk<Message> { every { messageId } returns 42 }
        val message = FlowMessage(FlowKeys.PUBLISH, "pick")

        val mutation = executor.execute(
            user, Locale.ROOT, mapOf("card" to 10),
            listOf(DeleteMessageAction("card"), SendMessageAction("card", message)),
        )

        mutation.replacements shouldBe mapOf("card" to 42)
        mutation.removed shouldBe emptySet()
    }

    should("drop the binding when a message sent earlier in the batch is deleted") {
        every { client.execute(any<org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages>()) } returns true
        every { client.execute(any<SendMessage>()) } returns mockk<Message> { every { messageId } returns 43 }
        val message = FlowMessage(FlowKeys.PUBLISH, "pick")

        val mutation = executor.execute(
            user, Locale.ROOT, emptyMap(),
            listOf(SendMessageAction("tmp", message), DeleteMessageAction("tmp")),
        )

        mutation.replacements shouldBe emptyMap()
        mutation.removed shouldBe setOf("tmp")
    }

    should("schedule auto-delete for temporary messages and keep regular ones") {
        val scheduler = mockk<java.util.concurrent.ScheduledExecutorService>(relaxed = true)
        val timed = TelegramFlowActionExecutor(client, ObjectMapper(), renderer, scheduler)
        every { client.execute(any<SendMessage>()) } returns mockk<Message> { every { messageId } returns 50 }

        timed.execute(
            user, Locale.ROOT, emptyMap(),
            listOf(
                SendMessageAction("temp", FlowMessage(FlowKeys.PUBLISH, "done", autoDeleteAfterSeconds = 3)),
                SendMessageAction("keep", FlowMessage(FlowKeys.PUBLISH, "pick")),
            ),
        )

        io.mockk.verify(exactly = 1) {
            scheduler.schedule(any<Runnable>(), 3L, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    should("build a url button that passes client-side validation and carries no callback data") {
        every { client.execute(capture(sent)) } returns mockk<Message> { every { messageId } returns 1 }
        val message = FlowMessage(
            FlowKeys.SHARED, "out",
            inlineButtons = listOf(FlowInlineButton.link("share", "https://t.me/share/url?url=x"), FlowInlineButton("close", FlowCallbackPayload("SHARED", "CLOSE"), 0, 1)),
        )
        executor.execute(user, Locale.ROOT, emptyMap(), listOf(SendMessageAction("main", message)))

        sent.captured.validate()
        val row = (sent.captured.replyMarkup as org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup).keyboard.single()
        row[0].url shouldBe "https://t.me/share/url?url=x"
        row[0].callbackData shouldBe null
        row[1].callbackData shouldBe "{\"flow\":\"SHARED\",\"data\":\"CLOSE\"}"
    }
})
