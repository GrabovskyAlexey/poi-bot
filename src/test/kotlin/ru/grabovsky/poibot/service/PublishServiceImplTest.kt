package ru.grabovsky.poibot.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import ru.grabovsky.poibot.entity.Chat
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.entity.SavedPlaceChat
import ru.grabovsky.poibot.entity.SavedPlaceChatId
import ru.grabovsky.poibot.entity.UserChatId
import ru.grabovsky.poibot.repository.ChatPublishCount
import ru.grabovsky.poibot.repository.ChatRepository
import ru.grabovsky.poibot.repository.SavedPlaceChatRepository
import ru.grabovsky.poibot.repository.SavedPlaceRepository
import ru.grabovsky.poibot.repository.UserChatRepository
import ru.grabovsky.poibot.service.interfaces.ChatMembershipChecker
import ru.grabovsky.poibot.service.interfaces.ChatService
import ru.grabovsky.poibot.service.interfaces.PublishNotAllowedException
import java.util.Optional

class PublishServiceImplTest : ShouldSpec({
    val chatService = mockk<ChatService>(relaxed = true)
    val chatRepository = mockk<ChatRepository>()
    val userChatRepository = mockk<UserChatRepository>()
    val savedPlaceRepository = mockk<SavedPlaceRepository>()
    val savedPlaceChatRepository = mockk<SavedPlaceChatRepository>(relaxed = true)
    val checker = mockk<ChatMembershipChecker>()
    val service = PublishServiceImpl(
        chatService, chatRepository, userChatRepository, savedPlaceRepository, savedPlaceChatRepository, checker,
    )

    beforeTest {
        clearMocks(chatService, chatRepository, userChatRepository, savedPlaceRepository, savedPlaceChatRepository, checker)
    }

    fun place(id: Long, owner: Long = 1L) = SavedPlace(id = id, ownerId = owner, placeId = 1L, name = "P$id")

    context("getUserGroups") {
        should("drop groups the user has left and keep groups that could not be verified") {
            val stayed = Chat(id = -1L, type = "group", title = "Stayed")
            val left = Chat(id = -2L, type = "group", title = "Left")
            val unknown = Chat(id = -3L, type = "group", title = "Unknown")
            every { chatService.getUserChats(1L) } returns listOf(stayed, left, unknown)
            every { checker.isMember(-1L, 1L) } returns true
            every { checker.isMember(-2L, 1L) } returns false
            every { checker.isMember(-3L, 1L) } returns null

            service.getUserGroups(1L) shouldBe listOf(stayed, unknown)

            verify { chatService.unlinkUser(1L, -2L) }
            verify(exactly = 0) { chatService.unlinkUser(1L, -3L) }
        }
    }

    context("setPublished") {
        should("refuse when the user is not linked to the chat") {
            every { chatRepository.findById(-1L) } returns Optional.of(Chat(id = -1L, type = "group"))
            every { userChatRepository.existsById(UserChatId(1L, -1L)) } returns false

            shouldThrow<PublishNotAllowedException> { service.setPublished(1L, listOf(5L), -1L, true) }
        }

        should("refuse when the bot was removed from the chat") {
            every { chatRepository.findById(-1L) } returns Optional.of(Chat(id = -1L, type = "group", isActive = false))

            shouldThrow<PublishNotAllowedException> { service.setPublished(1L, listOf(5L), -1L, true) }
        }

        should("publish only own records and skip already published") {
            every { chatRepository.findById(-1L) } returns Optional.of(Chat(id = -1L, type = "group"))
            every { userChatRepository.existsById(UserChatId(1L, -1L)) } returns true
            every { savedPlaceRepository.findByIdInAndOwnerId(listOf(5L, 6L, 7L), 1L) } returns listOf(place(5), place(6))
            every { savedPlaceChatRepository.existsById(SavedPlaceChatId(5L, -1L)) } returns true
            every { savedPlaceChatRepository.existsById(SavedPlaceChatId(6L, -1L)) } returns false
            every { savedPlaceChatRepository.save(any<SavedPlaceChat>()) } answers { firstArg() }

            val changed = service.setPublished(1L, listOf(5L, 6L, 7L), -1L, true)

            changed shouldBe 1
            verify { savedPlaceChatRepository.save(match<SavedPlaceChat> { it.id == SavedPlaceChatId(6L, -1L) && it.sharedBy == 1L }) }
        }

        should("unpublish records that are published") {
            every { chatRepository.findById(-1L) } returns Optional.of(Chat(id = -1L, type = "group"))
            every { userChatRepository.existsById(UserChatId(1L, -1L)) } returns true
            every { savedPlaceRepository.findByIdInAndOwnerId(listOf(5L), 1L) } returns listOf(place(5))
            every { savedPlaceChatRepository.existsById(SavedPlaceChatId(5L, -1L)) } returns true

            service.setPublished(1L, listOf(5L), -1L, false) shouldBe 1

            verify { savedPlaceChatRepository.deleteById(SavedPlaceChatId(5L, -1L)) }
        }
    }

    context("publishedCounts") {
        should("map repository rows and short-circuit on empty input") {
            every { savedPlaceChatRepository.countByChats(listOf(5L), listOf(-1L)) } returns listOf(
                object : ChatPublishCount {
                    override val chatId = -1L
                    override val total = 1L
                }
            )

            service.publishedCounts(listOf(5L), listOf(-1L)) shouldBe mapOf(-1L to 1)
            service.publishedCounts(emptyList(), listOf(-1L)) shouldBe emptyMap()
        }
    }

    context("unpublishAsModerator") {
        should("allow the owner without asking Telegram") {
            every { savedPlaceRepository.findPublishedInChatById(-1L, 5L) } returns place(5, owner = 1L)

            service.unpublishAsModerator(1L, -1L, 5L) shouldBe true

            verify { savedPlaceChatRepository.deleteById(SavedPlaceChatId(5L, -1L)) }
        }

        should("allow a chat admin") {
            every { savedPlaceRepository.findPublishedInChatById(-1L, 5L) } returns place(5, owner = 1L)
            every { checker.isAdmin(-1L, 2L) } returns true

            service.unpublishAsModerator(2L, -1L, 5L) shouldBe true
        }

        should("deny a regular member") {
            every { savedPlaceRepository.findPublishedInChatById(-1L, 5L) } returns place(5, owner = 1L)
            every { checker.isAdmin(-1L, 2L) } returns false

            service.unpublishAsModerator(2L, -1L, 5L) shouldBe false

            verify(exactly = 0) { savedPlaceChatRepository.deleteById(any()) }
        }

        should("return false when the place is not published in the chat") {
            every { savedPlaceRepository.findPublishedInChatById(-1L, 5L) } returns null

            service.unpublishAsModerator(1L, -1L, 5L) shouldBe false
        }
    }
})
