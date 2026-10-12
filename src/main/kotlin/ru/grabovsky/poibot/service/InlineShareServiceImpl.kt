package ru.grabovsky.poibot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.grabovsky.poibot.config.BotConfig
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.service.interfaces.*
import ru.grabovsky.poibot.strategy.flow.places.PlaceCardView
import ru.grabovsky.poibot.strategy.flow.places.PlaceFormatter
import ru.grabovsky.poibot.util.LocaleUtils

/**
 * Результат - карточка места (без личных тегов, заметки и статуса) и кнопка «Сохранить себе» с обычной ссылкой-приглашением,
 * поэтому получатель сохраняет место так же, как по ссылке из «Поделиться». Токены создаются для показанных мест.
 */
@Service
class InlineShareServiceImpl(
    private val telegramClient: TelegramClient,
    private val botConfig: BotConfig,
    private val userService: UserService,
    private val placeListService: PlaceListService,
    private val sharingService: SharingService,
    private val reviewService: ReviewService,
    private val messageGenerateService: MessageGenerateService,
    private val formatter: PlaceFormatter,
    private val i18n: I18nService,
) : InlineShareService {

    override fun answer(query: InlineQuery) {
        val userId = query.from.id
        val user = userService.getUser(userId)
        val locale = LocaleUtils.resolve(user)
        // Неизвестный боту пользователь мест не имеет: отвечаем пустым списком
        val places = user?.let {
            placeListService.searchOwn(userId, PlaceListQuery(text = query.query), 0, InlineShareService.MAX_RESULTS).items.map { it.place }
        }.orEmpty()
        val results = places.mapNotNull { article(userId, it, locale) }
        val answer = AnswerInlineQuery.builder()
            .inlineQueryId(query.id)
            .results(results)
            .cacheTime(0)
            .isPersonal(true)
            .build()
        runCatching { telegramClient.execute(answer) }
            .onFailure { logger.warn { "Failed to answer inline query ${query.id}: ${it.message}" } }
    }

    private fun article(userId: Long, place: SavedPlace, locale: java.util.Locale): InlineQueryResult? {
        val token = sharingService.getOrCreateToken(userId, place.id!!) ?: return null
        val text = messageGenerateService.processTemplate(
            "shared/in",
            PlaceCardView(
                name = place.name,
                address = place.address,
                description = formatter.shorten(place.description, MAX_DESCRIPTION),
                website = place.websiteUrl,
                distanceText = null,
                rating = formatter.rating(reviewService.summary(place.placeId)),
            ),
            locale,
        )
        val link = "https://t.me/${botConfig.name}?start=${SharingService.START_PREFIX}$token"
        val save = InlineKeyboardButton(i18n.i18n("buttons.shared.save", locale)).apply { url = link }
        return InlineQueryResultArticle.builder()
            .id(place.id.toString())
            .title(place.name)
            .description(place.address ?: place.description)
            .inputMessageContent(InputTextMessageContent.builder().messageText(text).parseMode("HTML").build())
            .replyMarkup(InlineKeyboardMarkup(listOf(InlineKeyboardRow(save))))
            .build()
    }

    private companion object {
        const val MAX_DESCRIPTION = 600
        val logger = KotlinLogging.logger {}
    }
}
