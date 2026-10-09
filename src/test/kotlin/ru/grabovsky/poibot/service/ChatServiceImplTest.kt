package ru.grabovsky.poibot.service

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import ru.grabovsky.poibot.entity.Chat
import ru.grabovsky.poibot.entity.UserChat
import ru.grabovsky.poibot.entity.UserChatId
import ru.grabovsky.poibot.repository.ChatRepository
import ru.grabovsky.poibot.repository.UserChatRepository
import java.util.Optional
import org.telegram.telegrambots.meta.api.objects.chat.Chat as TgChat

class ChatServiceImplTest : ShouldSpec({
    val chatRepository = mockk<ChatRepository>()
    val userChatRepository = mockk<UserChatRepository>()
    val service = ChatServiceImpl(chatRepository, userChatRepository)

    beforeTest { clearMocks(chatRepository, userChatRepository) }

    should("create chat when it is not registered yet") {
        val tgChat = mockk<TgChat> {
            every { id } returns -100L
            every { type } returns "supergroup"
            every { title } returns "Friends"
        }
        every { chatRepository.findById(-100L) } returns Optional.empty()
        val saved = slot<Chat>()
        every { chatRepository.save(capture(saved)) } answers { saved.captured }

        service.registerChat(tgChat)

        saved.captured.type shouldBe "supergroup"
        saved.captured.title shouldBe "Friends"
        saved.captured.isActive shouldBe true
    }

    should("keep real chat type when the same chat is shared via chat_shared") {
        val existing = Chat(id = -100L, type = "supergroup", title = "Old")
        every { chatRepository.findById(-100L) } returns Optional.of(existing)
        every { chatRepository.save(existing) } returns existing

        service.registerSharedChat(-100L, "New")

        existing.type shouldBe "supergroup"
        existing.title shouldBe "New"
    }

    should("create user link once and only refresh it later") {
        val id = UserChatId(1L, -100L)
        every { userChatRepository.findById(id) } returns Optional.empty()
        every { userChatRepository.save(any<UserChat>()) } answers { firstArg() }

        service.linkUser(1L, -100L)

        verify { userChatRepository.save(match<UserChat> { it.id == id }) }

        val link = UserChat(id)
        every { userChatRepository.findById(id) } returns Optional.of(link)

        service.linkUser(1L, -100L)

        verify(exactly = 2) { userChatRepository.save(any<UserChat>()) }
    }

    should("deactivate chat when bot is removed") {
        val chat = Chat(id = -100L, type = "group")
        every { chatRepository.findById(-100L) } returns Optional.of(chat)
        every { chatRepository.save(chat) } returns chat

        service.setActive(-100L, false)

        chat.isActive shouldBe false
    }

    should("change chat id on migration to supergroup") {
        every { chatRepository.existsById(-1L) } returns true
        every { chatRepository.existsById(-1001L) } returns false
        every { chatRepository.changeId(-1L, -1001L) } returns 1

        service.migrate(-1L, -1001L)

        verify { chatRepository.changeId(-1L, -1001L) }
    }

    should("skip migration when target chat already exists") {
        every { chatRepository.existsById(-1L) } returns true
        every { chatRepository.existsById(-1001L) } returns true

        service.migrate(-1L, -1001L)

        verify(exactly = 0) { chatRepository.changeId(any(), any()) }
    }
})
