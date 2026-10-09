package ru.grabovsky.poibot.strategy.flow.nearby

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.geo.NearbyResult
import ru.grabovsky.poibot.geo.SearchRadius
import ru.grabovsky.poibot.service.interfaces.I18nService
import ru.grabovsky.poibot.service.interfaces.NearbySearchService
import ru.grabovsky.poibot.service.interfaces.SavedPlaceService
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.core.support.buildMessage
import ru.grabovsky.poibot.strategy.flow.places.PlaceCardFactory
import ru.grabovsky.poibot.strategy.flow.places.PlaceFormatter
import java.util.*

enum class NearbyStep(override val key: String) : FlowStep {
    WAIT("wait"),
    RESULT("result"),
    RADIUS("radius"),
}

data class NearbyState(
    var lat: Double? = null,
    var lon: Double? = null,
    var radiusMeters: Int = SearchRadius.DEFAULT.meters,
)

data class PointView(val index: Int, val name: String, val distanceText: String)

data class HintView(val radiusText: String, val extra: Int, val total: Int)

/** Модель шаблона `nearby/result`. */
data class NearbyView(
    val radiusText: String,
    val points: List<PointView>,
    val hints: List<HintView>,
    val nothingAnywhere: Boolean,
    val hiddenCount: Int,
)

/** Модель шаблона `nearby/radius`. */
data class RadiusView(val currentText: String)

/**
 * Поиск своих точек рядом. Ищем всегда по максимальному радиусу, показываем выбранный радиус
 * и сводку-подсказки по большим радиусам (только количество, без данных точек).
 */
@Component
class NearbyFlow(
    private val nearbySearchService: NearbySearchService,
    private val savedPlaceService: SavedPlaceService,
    private val userService: UserService,
    private val cardFactory: PlaceCardFactory,
    private val formatter: PlaceFormatter,
    private val i18n: I18nService,
) : FlowHandler<NearbyState> {

    override val key: FlowKey = FlowKeys.NEARBY
    override val payloadType: Class<NearbyState> = NearbyState::class.java

    override fun start(context: FlowStartContext): FlowResult<NearbyState> {
        val state = NearbyState(radiusMeters = savedRadius(context.user.id))
        val message = key.buildMessage(
            step = NearbyStep.WAIT,
            model = WaitView(formatter.distance(state.radiusMeters, context.locale)),
            replyButtons = listOf(FlowReplyButton(i18n.i18n("buttons.nearby.send_location", context.locale), requestLocation = true)),
            parseMode = FlowParseMode.HTML,
        )
        return FlowResult(NearbyStep.WAIT.key, state, listOf(SendMessageAction(WAIT_BINDING, message)))
    }

    override fun onMessage(context: FlowContext<NearbyState>, message: Message): FlowResult<NearbyState>? {
        val location = when {
            message.hasLocation() -> message.location
            (message.venue != null) -> message.venue.location
            else -> return null
        }
        val state = context.state.payload
        state.lat = location.latitude
        state.lon = location.longitude
        state.radiusMeters = savedRadius(message.from.id)
        val actions = listOf(
            DeleteMessageIdAction(message.messageId),
            DeleteMessageAction(WAIT_BINDING),
            DeleteMessageAction(RESULT_BINDING),
            DeleteMessageAction(CARD_BINDING),
            SendMessageAction(RESULT_BINDING, resultMessage(message.from.id, state, context.locale)),
        )
        return FlowResult(NearbyStep.RESULT.key, state, actions)
    }

    override fun onCallback(
        context: FlowContext<NearbyState>,
        callbackQuery: CallbackQuery,
        data: String,
    ): FlowResult<NearbyState>? {
        val (command, argument) = parseCallback(data)
        val userId = callbackQuery.from.id
        val state = context.state.payload
        val locale = context.locale
        val answer = AnswerCallbackAction(callbackQuery.id)
        val id = argument?.toLongOrNull()

        val actions: List<FlowAction> = when (command) {
            "R" -> {
                val radius = SearchRadius.fromMeters(argument?.toIntOrNull())
                if (state.lat == null) return null
                state.radiusMeters = radius.meters
                saveRadius(userId, radius)
                listOf(EditMessageAction(RESULT_BINDING, resultMessage(userId, state, locale)), answer)
            }

            "RADIUS" -> listOf(EditMessageAction(RESULT_BINDING, radiusMessage(state, locale)), answer)

            "BACK" -> listOf(EditMessageAction(RESULT_BINDING, resultMessage(userId, state, locale)), answer)

            "OPEN" -> {
                val place = id?.let { savedPlaceService.get(userId, it) } ?: return null
                val distance = distanceFromUser(state, place.lat, place.lon)
                listOf<FlowAction>(DeleteMessageAction(CARD_BINDING)) +
                        cardFactory.cardActions(key, place, locale, CARD_BINDING, manage = false, distanceMeters = distance) + answer
            }

            "MAP" -> {
                val place = id?.let { savedPlaceService.get(userId, it) } ?: return null
                listOf(
                    SendVenueAction(null, place.name, place.address, place.lat ?: return null, place.lon ?: return null),
                    answer,
                )
            }

            "CLOSE" -> listOf(DeleteMessageAction(CARD_BINDING), answer)

            else -> return null
        }
        return FlowResult(NearbyStep.RESULT.key, state, actions)
    }

    private fun resultMessage(userId: Long, state: NearbyState, locale: Locale): FlowMessage {
        val selected = SearchRadius.fromMeters(state.radiusMeters)
        val result = nearbySearchService.searchOwn(userId, state.lat!!, state.lon!!, selected)
        val shown = result.points.take(MAX_SHOWN)
        val view = NearbyView(
            radiusText = formatter.distance(selected.meters, locale),
            points = shown.mapIndexed { index, point ->
                PointView(index + 1, point.place.name, formatter.distance(point.distanceMeters, locale))
            },
            hints = result.hints.map { HintView(formatter.distance(it.radius.meters, locale), it.extra, it.total) },
            nothingAnywhere = result.points.isEmpty() && result.hints.isEmpty(),
            hiddenCount = result.points.size - shown.size,
        )
        return key.buildMessage(
            step = NearbyStep.RESULT,
            model = view,
            inlineButtons = buttons(result, shown, locale),
            parseMode = FlowParseMode.HTML,
        )
    }

    private fun buttons(
        result: NearbyResult,
        shown: List<ru.grabovsky.poibot.geo.NearbyPoint>,
        locale: Locale,
    ): List<FlowInlineButton> {
        val buttons = mutableListOf<FlowInlineButton>()
        var row = 0
        shown.forEachIndexed { index, point ->
            val label = "${index + 1}. ${point.place.name.take(BUTTON_NAME_LENGTH)} · ${formatter.distance(point.distanceMeters, locale)}"
            buttons += FlowInlineButton(label, FlowCallbackPayload(key.value, "OPEN:${point.place.id}"), row++)
        }
        result.hints.forEach { hint ->
            val label = i18n.i18n("buttons.nearby.hint", locale, null, formatter.distance(hint.radius.meters, locale), hint.extra, hint.total)
            buttons += FlowInlineButton(label, FlowCallbackPayload(key.value, "R:${hint.radius.meters}"), row++)
        }
        buttons += FlowInlineButton(i18n.i18n("buttons.nearby.change_radius", locale), FlowCallbackPayload(key.value, "RADIUS"), row)
        return buttons
    }

    private fun radiusMessage(state: NearbyState, locale: Locale): FlowMessage {
        val buttons = SearchRadius.entries.mapIndexed { index, radius ->
            val mark = if (radius.meters == state.radiusMeters) "✅ " else ""
            FlowInlineButton(
                "$mark${formatter.distance(radius.meters, locale)}",
                FlowCallbackPayload(key.value, "R:${radius.meters}"),
                row = 0,
                col = index,
            )
        } + FlowInlineButton(i18n.i18n("buttons.common.back", locale), FlowCallbackPayload(key.value, "BACK"), row = 1)
        return key.buildMessage(
            step = NearbyStep.RADIUS,
            model = RadiusView(formatter.distance(state.radiusMeters, locale)),
            inlineButtons = buttons,
            parseMode = FlowParseMode.HTML,
        )
    }

    private fun distanceFromUser(state: NearbyState, lat: Double?, lon: Double?): Int? {
        val fromLat = state.lat ?: return null
        val fromLon = state.lon ?: return null
        if (lat == null || lon == null) return null
        return ru.grabovsky.poibot.geo.GeoUtils.distanceMeters(fromLat, fromLon, lat, lon).toInt()
    }

    private fun savedRadius(userId: Long): Int =
        SearchRadius.fromMeters(userService.getUser(userId)?.profile?.settings?.searchRadiusMeters).meters

    private fun saveRadius(userId: Long, radius: SearchRadius) {
        val user = userService.getUser(userId) ?: return
        val profile = user.profile ?: return
        if (profile.settings.searchRadiusMeters == radius.meters) return
        profile.settings.searchRadiusMeters = radius.meters
        userService.saveUser(user)
    }

    /** Модель шаблона `nearby/wait`. */
    data class WaitView(val radiusText: String)

    private companion object {
        const val WAIT_BINDING = "wait"
        const val RESULT_BINDING = "result"
        const val CARD_BINDING = "card"
        const val MAX_SHOWN = 10
        const val BUTTON_NAME_LENGTH = 30
    }
}
