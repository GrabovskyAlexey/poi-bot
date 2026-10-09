package ru.grabovsky.poibot.service

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.send.SendVenue
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.Venue
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.location.Location
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ForceReplyKeyboard
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.grabovsky.poibot.config.BotConfig
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.geo.NearbyPoint
import ru.grabovsky.poibot.geo.NearbyResult
import ru.grabovsky.poibot.geo.SearchRadius
import ru.grabovsky.poibot.service.interfaces.*
import ru.grabovsky.poibot.strategy.flow.KeyI18n
import ru.grabovsky.poibot.strategy.flow.places.PlaceFormatter
import java.io.Serializable
import org.telegram.telegrambots.meta.api.objects.User as TgUser

class GroupPlacesServiceImplTest : ShouldSpec({
    val executed = mutableListOf<Any>()
    val telegramClient = mockk<TelegramClient>()
    val userService = mockk<UserService>(relaxed = true)
    val chatService = mockk<ChatService>(relaxed = true)
    val publishService = mockk<PublishService>()
    val nearbySearchService = mockk<NearbySearchService>()
    val i18n = KeyI18n()
    val freeMarker = FreeMarkerConfigurer().apply {
        setTemplateLoaderPath("classpath:/message/template")
        setDefaultEncoding("UTF-8")
        afterPropertiesSet()
    }
    val service = GroupPlacesServiceImpl(
        telegramClient, BotConfig("token", "PoiBot"), userService, chatService, publishService,
        nearbySearchService, MessageGenerateServiceImpl(freeMarker), PlaceFormatter(i18n), i18n,
    )

    val group = mockk<Chat> {
        every { id } returns -100L
        every { isUserChat } returns false
    }
    val member = mockk<TgUser> {
        every { id } returns 5L
        every { isBot } returns false
    }

    beforeTest {
        clearMocks(userService, chatService, publishService, nearbySearchService)
        executed.clear()
        every { userService.getUser(any()) } returns null
        every { telegramClient.execute(any<BotApiMethod<Serializable>>()) } answers {
            executed += firstArg<Any>()
            null
        }
        every { telegramClient.execute(any<SendPhoto>()) } answers {
            executed += firstArg<Any>()
            null
        }
        every { telegramClient.execute(any<SendVenue>()) } answers {
            executed += firstArg<Any>()
            null
        }
    }

    fun place(id: Long, name: String = "Хмель", photo: String? = null) = SavedPlace(
        id = id, ownerId = 1L, placeId = 1L, name = name, lat = 55.0, lon = 37.0, photoFileId = photo,
    )

    fun markupData(markup: Any?): List<String> =
        (markup as InlineKeyboardMarkup).keyboard.flatMap { row -> row.map { it.callbackData ?: it.url } }

    fun callbackOf(data: String, messageChat: Chat = group, messageId: Int = 10): CallbackQuery {
        val message = mockk<Message> {
            every { chat } returns messageChat
            every { chatId } returns messageChat.id
            every { this@mockk.messageId } returns messageId
        }
        return mockk {
            every { id } returns "cb"
            every { from } returns member
            every { this@mockk.message } returns message
        }
    }

    should("list published places of the chat as a single reply and remember the member") {
        every { publishService.listPublished(-100L, 0, any()) } returns
                SavedPlacePage(listOf(place(1), place(2, "Бар")), 0, 1, 2)

        service.showPlaces(group, member, 77)

        verify { chatService.linkUser(5L, -100L) }
        val sent = executed.single().shouldBeInstanceOf<SendMessage>()
        sent.text shouldContain "Места чата"
        sent.text shouldContain "Хмель"
        sent.replyToMessageId shouldBe 77
        markupData(sent.replyMarkup).map { it.substringAfter("\"data\":\"").substringBefore("\"") } shouldBe listOf("O:1", "O:2")
    }

    should("explain how to publish places when nothing is published") {
        every { publishService.listPublished(-100L, 0, any()) } returns SavedPlacePage(emptyList(), 0, 1, 0)

        service.showPlaces(group, member, null)

        executed.single().shouldBeInstanceOf<SendMessage>().text shouldContain "нет опубликованных мест"
    }

    should("ask for location with a selective force reply addressed to the requester") {
        service.askNearbyLocation(group, member, 78)

        val sent = executed.single().shouldBeInstanceOf<SendMessage>()
        sent.replyToMessageId shouldBe 78
        val markup = sent.replyMarkup.shouldBeInstanceOf<ForceReplyKeyboard>()
        markup.selective shouldBe true
        markup.forceReply shouldBe true
    }

    should("show a hint with a link to the private chat on add") {
        service.showAddHint(group, member, 79)

        val sent = executed.single().shouldBeInstanceOf<SendMessage>()
        sent.text shouldContain "в личке"
        markupData(sent.replyMarkup) shouldBe listOf("https://t.me/PoiBot")
    }

    context("handleMessage") {
        fun locationReply(repliedUser: TgUser?, location: Location? = null, venue: Venue? = null): Message {
            val reply = mockk<Message> { every { from } returns repliedUser }
            return mockk {
                every { replyToMessage } returns reply
                every { hasLocation() } returns (location != null)
                every { this@mockk.location } returns location
                every { this@mockk.venue } returns venue
                every { from } returns member
                every { chat } returns group
                every { chatId } returns -100L
                every { messageId } returns 90
            }
        }

        val botUser = mockk<TgUser> {
            every { isBot } returns true
            every { userName } returns "poibot"
        }
        val point = mockk<Location> {
            every { latitude } returns 55.0
            every { longitude } returns 37.0
        }

        should("answer a location sent in reply to the bot with places of the chat") {
            every { nearbySearchService.searchInChat(-100L, 55.0, 37.0, SearchRadius.M250) } returns
                    NearbyResult.build(listOf(NearbyPoint(place(1), 80), NearbyPoint(place(2, "Далеко"), 400)), SearchRadius.M250)

            service.handleMessage(locationReply(botUser, point)) shouldBe true

            val sent = executed.single().shouldBeInstanceOf<SendMessage>()
            sent.text shouldContain "Хмель"
            sent.replyToMessageId shouldBe 90
            markupData(sent.replyMarkup).map { it.substringAfter("\"data\":\"").substringBefore("\"") } shouldBe
                    listOf("O:1", "R:500:55.00000:37.00000")
        }

        should("ignore a location that replies to somebody else") {
            val other = mockk<TgUser> {
                every { isBot } returns false
                every { userName } returns "alex"
            }

            service.handleMessage(locationReply(other, point)) shouldBe false
            executed shouldHaveSize 0
        }

        should("ignore a reply to another bot") {
            val anotherBot = mockk<TgUser> {
                every { isBot } returns true
                every { userName } returns "other_bot"
            }

            service.handleMessage(locationReply(anotherBot, point)) shouldBe false
        }

        should("ignore a text reply to the bot") {
            service.handleMessage(locationReply(botUser, null)) shouldBe false
            executed shouldHaveSize 0
        }
    }

    context("callbacks") {
        should("open a card as a reply with a photo and a remove button") {
            every { publishService.getPublished(-100L, 1L) } returns place(1, photo = "file1")

            service.onCallback(callbackOf("O:1"), "O:1")

            val photo = executed.filterIsInstance<SendPhoto>().single()
            photo.replyToMessageId shouldBe 10
            markupData(photo.replyMarkup).map { it.substringAfter("\"data\":\"").substringBefore("\"") } shouldBe
                    listOf("M:1", "C", "X:1")
        }

        should("send the place on the map") {
            every { publishService.getPublished(-100L, 1L) } returns place(1)

            service.onCallback(callbackOf("M:1"), "M:1")

            executed.filterIsInstance<SendVenue>().single().latitude shouldBe 55.0
        }

        should("remove the place and the card when the user may moderate") {
            every { publishService.unpublishAsModerator(5L, -100L, 1L) } returns true

            service.onCallback(callbackOf("X:1"), "X:1")

            executed.filterIsInstance<DeleteMessage>().single().messageId shouldBe 10
            executed.filterIsInstance<AnswerCallbackQuery>().single().text shouldBe "alerts.group.removed"
        }

        should("refuse removal for a regular member") {
            every { publishService.unpublishAsModerator(5L, -100L, 1L) } returns false

            service.onCallback(callbackOf("X:1"), "X:1")

            executed.filterIsInstance<DeleteMessage>().size shouldBe 0
            val answer = executed.filterIsInstance<AnswerCallbackQuery>().single()
            answer.text shouldBe "alerts.group.not_allowed"
            answer.showAlert shouldBe true
        }

        should("turn the page by editing the same message") {
            every { publishService.listPublished(-100L, 1, any()) } returns
                    SavedPlacePage(listOf(place(9, "Девятый")), 1, 2, 9)

            service.onCallback(callbackOf("P:1"), "P:1")

            val edit = executed.filterIsInstance<EditMessageText>().single()
            edit.messageId shouldBe 10
            edit.text shouldContain "Девятый"
            markupData(edit.replyMarkup).map { it.substringAfter("\"data\":\"").substringBefore("\"") } shouldBe
                    listOf("O:9", "P:0")
        }

        should("recompute nearby result for the chosen radius") {
            every { nearbySearchService.searchInChat(-100L, 55.5, 37.5, SearchRadius.M500) } returns
                    NearbyResult.build(listOf(NearbyPoint(place(1), 300)), SearchRadius.M500)

            service.onCallback(callbackOf("R:500:55.50000:37.50000"), "R:500:55.50000:37.50000")

            executed.filterIsInstance<EditMessageText>().single().text shouldContain "500"
        }

        should("close the card by deleting it") {
            service.onCallback(callbackOf("C"), "C")

            executed.filterIsInstance<DeleteMessage>().single().messageId shouldBe 10
        }
    }
})
