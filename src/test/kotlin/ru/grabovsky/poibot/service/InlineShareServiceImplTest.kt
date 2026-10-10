package ru.grabovsky.poibot.service

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.grabovsky.poibot.config.BotConfig
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.entity.User
import ru.grabovsky.poibot.entity.UserProfile
import ru.grabovsky.poibot.service.interfaces.*
import ru.grabovsky.poibot.strategy.flow.KeyI18n
import ru.grabovsky.poibot.strategy.flow.places.PlaceFormatter
import org.telegram.telegrambots.meta.api.objects.User as TgUser

class InlineShareServiceImplTest : ShouldSpec({
    val client = mockk<TelegramClient>()
    val userService = mockk<UserService>()
    val listService = mockk<PlaceListService>()
    val sharing = mockk<SharingService>()
    val reviews = mockk<ReviewService> {
        every { summary(any()) } returns RatingSummary.EMPTY
    }
    val i18n = KeyI18n()
    val freeMarker = FreeMarkerConfigurer().apply {
        setTemplateLoaderPath("classpath:/message/template")
        setDefaultEncoding("UTF-8")
        afterPropertiesSet()
    }
    val service = InlineShareServiceImpl(
        client, BotConfig("token", "PoiBot"), userService, listService, sharing, reviews,
        MessageGenerateServiceImpl(freeMarker), PlaceFormatter(i18n), i18n,
    )
    val place = SavedPlace(
        id = 5L, ownerId = 9L, placeId = 77L, name = "Хмель", address = "Ленина 5", description = "Крафт",
        status = "WANT", note = "секретная заметка", tags = listOf("секретныйтег"),
    )

    fun query(text: String): InlineQuery = mockk {
        every { id } returns "q1"
        every { from } returns mockk<TgUser> { every { id } returns 9L }
        every { this@mockk.query } returns text
    }

    fun page(vararg places: SavedPlace) =
        PlaceListPage(places.map { PlaceListEntry(it, RatingSummary.EMPTY) }, 0, 1, places.size.toLong(), places.size.toLong())

    beforeTest { clearMocks(client, userService, listService, sharing) }

    should("answer with the user's places searched by the typed text") {
        every { userService.getUser(9L) } returns User(9L, "T", null, "t").apply { profile = UserProfile() }
        every { listService.searchOwn(9L, PlaceListQuery(text = "хм"), 0, InlineShareService.MAX_RESULTS) } returns page(place)
        every { sharing.getOrCreateToken(9L, 5L) } returns "tok"
        val sent = slot<AnswerInlineQuery>()
        every { client.execute(capture(sent)) } returns true

        service.answer(query("хм"))

        sent.captured.inlineQueryId shouldBe "q1"
        sent.captured.isPersonal shouldBe true
        val article = sent.captured.results.single().shouldBeInstanceOf<InlineQueryResultArticle>()
        article.id shouldBe "5"
        article.title shouldBe "Хмель"
        val text = article.inputMessageContent.shouldBeInstanceOf<InputTextMessageContent>().messageText
        text shouldContain "Хмель"
        text shouldContain "Ленина 5"
        // Личные поля владельца в карточку не попадают
        text shouldNotContain "секретная заметка"
        text shouldNotContain "секретныйтег"
        article.replyMarkup.keyboard.single().single().url shouldBe "https://t.me/PoiBot?start=sp_tok"
    }

    should("answer with an empty list for an unknown user") {
        every { userService.getUser(9L) } returns null
        val sent = slot<AnswerInlineQuery>()
        every { client.execute(capture(sent)) } returns true

        service.answer(query(""))

        sent.captured.results shouldHaveSize 0
        verify(exactly = 0) { listService.searchOwn(any(), any(), any(), any()) }
    }

    should("not fail when Telegram rejects the answer") {
        every { userService.getUser(9L) } returns null
        every { client.execute(any<AnswerInlineQuery>()) } throws RuntimeException("boom")

        service.answer(query(""))
    }
})
