package ru.grabovsky.poibot.strategy.flow.shared

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.config.BotConfig
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.service.interfaces.AcceptResult
import ru.grabovsky.poibot.service.interfaces.I18nService
import ru.grabovsky.poibot.service.interfaces.ReviewService
import ru.grabovsky.poibot.service.interfaces.SavedPlaceService
import ru.grabovsky.poibot.service.interfaces.SharingService
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.core.support.buildMessage
import ru.grabovsky.poibot.strategy.flow.places.PlaceCardView
import ru.grabovsky.poibot.strategy.flow.places.PlaceFormatter
import java.util.*

enum class SharedStep(override val key: String) : FlowStep {
    IN("in"),
    OUT("out"),
    SAVED("saved"),
    INVALID("invalid"),
}

/** Состояние не нужно: все кнопки несут токен, поэтому старые сообщения продолжают работать. */
data class SharedState(
    var token: String? = null,
    /** true сразу после запуска по callback без состояния: ближайший такой callback уже учтён в start(). */
    var fresh: Boolean = false,
)

/** Модель шаблона `shared/out`. */
data class SharedOutView(val name: String, val link: String)

/** Модель шаблона `shared/saved`. */
data class SharedSavedView(val name: String)

/**
 * Шаринг мест по ссылке. Исходящая сторона: `OUT:<id>` — бот присылает ссылку на место.
 * Входящая: `/start sp_<token>` — карточка с кнопкой «Сохранить себе».
 */
@Component
class SharedFlow(
    private val sharingService: SharingService,
    private val savedPlaceService: SavedPlaceService,
    private val reviewService: ReviewService,
    private val botConfig: BotConfig,
    private val formatter: PlaceFormatter,
    private val i18n: I18nService,
) : FlowHandler<SharedState> {

    override val key: FlowKey = FlowKeys.SHARED
    override val payloadType: Class<SharedState> = SharedState::class.java

    override fun start(context: FlowStartContext): FlowResult<SharedState> {
        val args = context.args.orEmpty()
        val userId = context.user.id
        val locale = context.locale
        return when {
            args.startsWith(SharingService.START_PREFIX) -> {
                val token = args.removePrefix(SharingService.START_PREFIX)
                val place = sharingService.findShared(token)
                if (place == null) invalid(locale) else incoming(token, place, locale)
            }

            args.startsWith("OUT:") -> outResult(userId, args.removePrefix("OUT:").toLongOrNull(), locale)
                ?.also { it.payload.fresh = true } ?: invalid(locale)

            // Кнопка старого сообщения без состояния: обработает onCallback
            args.startsWith("SAVE:") || args.startsWith("MAP:") || args == "CLOSE" ->
                FlowResult(SharedStep.IN.key, SharedState(), emptyList())

            else -> invalid(locale)
        }
    }

    override fun onMessage(context: FlowContext<SharedState>, message: Message): FlowResult<SharedState>? = null

    override fun onCallback(
        context: FlowContext<SharedState>,
        callbackQuery: CallbackQuery,
        data: String,
    ): FlowResult<SharedState>? {
        val (command, argument) = parseCallback(data)
        val state = context.state.payload
        val locale = context.locale
        val answer = AnswerCallbackAction(callbackQuery.id)
        val cardMessageId = callbackQuery.message?.messageId

        val actions: List<FlowAction> = when (command) {
            "SAVE" -> {
                val token = argument ?: return null
                when (val result = sharingService.accept(callbackQuery.from.id, token)) {
                    is AcceptResult.Saved -> listOfNotNull(
                        cardMessageId?.let { DeleteMessageIdAction(it) },
                        SendMessageAction(null, savedMessage(result.place.name)),
                        answer,
                    )

                    AcceptResult.AlreadySaved -> listOf(alert(callbackQuery, "alerts.shared.already_saved", locale))
                    AcceptResult.OwnPlace -> listOf(alert(callbackQuery, "alerts.shared.own_place", locale))
                    AcceptResult.LimitReached -> listOf(alert(callbackQuery, "alerts.add.limit", locale))
                    AcceptResult.NotFound -> listOfNotNull(
                        alert(callbackQuery, "alerts.shared.invalid_link", locale),
                        cardMessageId?.let { DeleteMessageIdAction(it) },
                    )
                }
            }

            "MAP" -> {
                val place = argument?.let(sharingService::findShared)
                val lat = place?.lat
                val lon = place?.lon
                if (place != null && lat != null && lon != null) {
                    listOf(SendVenueAction(null, place.name, place.address, lat, lon), answer)
                } else {
                    listOf(answer)
                }
            }

            "CLOSE" -> listOfNotNull(cardMessageId?.let { DeleteMessageIdAction(it) }, answer)

            "OUT" -> {
                if (state.fresh) {
                    state.fresh = false
                    listOf(answer)
                } else {
                    val again = outResult(callbackQuery.from.id, argument?.toLongOrNull(), locale)
                        ?: return FlowResult(SharedStep.IN.key, state, listOf(alert(callbackQuery, "alerts.place.not_found", locale)))
                    return FlowResult(again.stepKey, again.payload, again.actions + answer)
                }
            }

            else -> return null
        }
        return FlowResult(SharedStep.IN.key, state, actions)
    }

    // --- сообщения ---------------------------------------------------------------------------

    private fun incoming(token: String, place: SavedPlace, locale: Locale): FlowResult<SharedState> {
        val message = key.buildMessage(
            step = SharedStep.IN,
            model = PlaceCardView(
                name = place.name,
                address = place.address,
                description = formatter.shorten(place.description, MAX_DESCRIPTION),
                website = place.websiteUrl,
                distanceText = null,
                rating = formatter.rating(reviewService.summary(place.placeId)),
            ),
            inlineButtons = buildList {
                add(button("buttons.shared.save", locale, "SAVE:$token", 0, 0))
                if (place.hasLocation()) add(button("buttons.places.map", locale, "MAP:$token", 0, 1))
                val comments = reviewService.commentCount(place.placeId)
                if (comments > 0) {
                    add(
                        FlowInlineButton(
                            i18n.i18n("buttons.reviews.comments", locale, null, comments),
                            FlowCallbackPayload(FlowKeys.REVIEWS.value, "COM:${place.placeId}:0"), 1, 0,
                        )
                    )
                }
                add(button("buttons.places.close", locale, "CLOSE", FlowInlineButton.LAST_ROW, 0))
            },
            parseMode = FlowParseMode.HTML,
        )
        val photo = place.photoFileId
        val action = if (photo != null) SendPhotoAction(MAIN_BINDING, photo, message) else SendMessageAction(MAIN_BINDING, message)
        return FlowResult(SharedStep.IN.key, SharedState(token), listOf(action))
    }

    /** Сообщение со ссылкой на своё место или null, если места нет / оно чужое. */
    private fun outResult(userId: Long, placeId: Long?, locale: Locale): FlowResult<SharedState>? {
        val place = placeId?.let { savedPlaceService.get(userId, it) } ?: return null
        val token = sharingService.getOrCreateToken(userId, place.id!!) ?: return null
        return outgoing(token, place.name, locale)
    }

    private fun outgoing(token: String, name: String, locale: Locale): FlowResult<SharedState> {
        val link = "https://t.me/${botConfig.name}?start=${SharingService.START_PREFIX}$token"
        val message = key.buildMessage(
            step = SharedStep.OUT,
            model = SharedOutView(name = name, link = link),
            inlineButtons = listOf(
                FlowInlineButton.link(i18n.i18n("buttons.shared.send", locale), shareUrl(link, name, locale), 0, 0),
                button("buttons.places.close", locale, "CLOSE", FlowInlineButton.LAST_ROW, 0),
            ),
            parseMode = FlowParseMode.HTML,
        )
        return FlowResult(SharedStep.OUT.key, SharedState(token), listOf(SendMessageAction(MAIN_BINDING, message)))
    }

    /** Ссылка t.me/share/url открывает выбор чата Telegram с уже подставленным текстом и ссылкой. */
    private fun shareUrl(link: String, name: String, locale: Locale): String {
        val text = i18n.i18n("shared.message", locale, null, name)
        return "https://t.me/share/url?url=${encode(link)}&text=${encode(text)}"
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private fun invalid(locale: Locale): FlowResult<SharedState> {
        val message = key.buildMessage(
            step = SharedStep.INVALID,
            parseMode = FlowParseMode.HTML,
            autoDeleteAfterSeconds = TEMP_VISIBLE_SECONDS,
        )
        return FlowResult(SharedStep.INVALID.key, SharedState(), listOf(SendMessageAction(null, message)), completed = true)
    }

    private fun savedMessage(name: String): FlowMessage =
        key.buildMessage(
            step = SharedStep.SAVED,
            model = SharedSavedView(name),
            parseMode = FlowParseMode.HTML,
            autoDeleteAfterSeconds = TEMP_VISIBLE_SECONDS,
        )

    private fun alert(callbackQuery: CallbackQuery, textKey: String, locale: Locale) =
        AnswerCallbackAction(callbackQuery.id, i18n.i18n(textKey, locale), showAlert = true)

    private fun button(textKey: String, locale: Locale, data: String, row: Int, col: Int) =
        FlowInlineButton(i18n.i18n(textKey, locale), FlowCallbackPayload(key.value, data), row, col)

    private companion object {
        const val MAIN_BINDING = "main"
        const val MAX_DESCRIPTION = 600
        const val TEMP_VISIBLE_SECONDS = 10
    }
}
