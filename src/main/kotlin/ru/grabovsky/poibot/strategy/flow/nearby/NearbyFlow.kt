package ru.grabovsky.poibot.strategy.flow.nearby

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.entity.PlaceStatus
import ru.grabovsky.poibot.geo.NearbyFilter
import ru.grabovsky.poibot.geo.NearbyResult
import ru.grabovsky.poibot.geo.SearchRadius
import ru.grabovsky.poibot.service.interfaces.AcceptResult
import ru.grabovsky.poibot.service.interfaces.I18nService
import ru.grabovsky.poibot.service.interfaces.NearbySearchService
import ru.grabovsky.poibot.service.interfaces.SavedPlaceService
import ru.grabovsky.poibot.service.interfaces.SharingService
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
    FILTER("filter"),
    SEARCHING("searching"),
}

data class NearbyState(
    var lat: Double? = null,
    var lon: Double? = null,
    var radiusMeters: Int = SearchRadius.DEFAULT.meters,
    /** Фильтр по личным полям: статус ([PlaceStatus]) и тег. Живёт, пока открыт этот диалог. */
    var status: String? = null,
    var tag: String? = null,
    /** Теги-подсказки экрана фильтра (кнопки несут индекс: лимит callback-данных). */
    var tagChoices: MutableList<String> = mutableListOf(),
) {
    fun filter() = NearbyFilter(status, tag)
}

data class PointView(val index: Int, val name: String, val distanceText: String, val group: Boolean = false)

data class HintView(val radiusText: String, val extra: Int, val total: Int)

/** Модель шаблона `nearby/result`. */
data class NearbyView(
    val radiusText: String,
    val points: List<PointView>,
    val hints: List<HintView>,
    val nothingAnywhere: Boolean,
    val hiddenCount: Int,
    /** Среди показанных есть места из групп (в шаблоне поясняется значок 👥). */
    val hasGroup: Boolean = false,
    /** Описание включённого фильтра (статус и тег) или null. */
    val filterText: String? = null,
)

/** Модель шаблона `nearby/radius`. */
data class RadiusView(val currentText: String)

/** Модель шаблона `nearby/filter`. */
data class FilterView(val hasTags: Boolean, val filterText: String?)

/**
 * Поиск своих точек рядом. Ищем всегда по максимальному радиусу, показываем выбранный радиус
 * и сводку-подсказки по большим радиусам (только количество, без данных точек).
 */
@Component
class NearbyFlow(
    private val nearbySearchService: NearbySearchService,
    private val savedPlaceService: SavedPlaceService,
    private val sharingService: SharingService,
    private val userService: UserService,
    private val cardFactory: PlaceCardFactory,
    private val formatter: PlaceFormatter,
    private val i18n: I18nService,
) : FlowHandler<NearbyState> {

    override val key: FlowKey = FlowKeys.NEARBY
    override val payloadType: Class<NearbyState> = NearbyState::class.java

    override fun start(context: FlowStartContext): FlowResult<NearbyState> {
        val state = NearbyState(radiusMeters = savedRadius(context.user.id))
        startLocation(context.args)?.let { (lat, lon) ->
            // Геопозиция, присланная вне диалога: сразу показываем места рядом
            state.lat = lat
            state.lon = lon
            return FlowResult(
                NearbyStep.RESULT.key, state,
                listOf(SendMessageAction(RESULT_BINDING, resultMessage(context.user.id, state, context.locale))),
            )
        }
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
            SendMessageAction(SEARCH_BINDING, searchingMessage()),
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

            "FILTER" -> {
                state.tagChoices = savedPlaceService.popularTags(userId).take(MAX_TAG_CHOICES).toMutableList()
                listOf(EditMessageAction(RESULT_BINDING, filterMessage(state, locale)), answer)
            }

            "FS" -> {
                val target = PlaceStatus.fromCode(argument) ?: return null
                state.status = PlaceStatus.toggle(PlaceStatus.fromCode(state.status), target)?.name
                listOf(EditMessageAction(RESULT_BINDING, filterMessage(state, locale)), answer)
            }

            "FT" -> {
                val tag = argument?.toIntOrNull()?.let { state.tagChoices.getOrNull(it) } ?: return null
                state.tag = tag.takeIf { it != state.tag }
                listOf(EditMessageAction(RESULT_BINDING, filterMessage(state, locale)), answer)
            }

            "FR" -> {
                state.status = null
                state.tag = null
                listOf(EditMessageAction(RESULT_BINDING, filterMessage(state, locale)), answer)
            }

            "OPEN" -> {
                val own = id?.let { savedPlaceService.get(userId, it) }
                val distance = distanceFromUser(state, own?.lat, own?.lon)
                if (own != null) {
                    listOf<FlowAction>(DeleteMessageAction(CARD_BINDING)) +
                            cardFactory.cardActions(key, own, locale, CARD_BINDING, manage = false, distanceMeters = distance) + answer
                } else {
                    val group = id?.let { nearbySearchService.findInUserGroups(userId, it) } ?: return null
                    listOf<FlowAction>(DeleteMessageAction(CARD_BINDING)) +
                            cardFactory.groupCardActions(
                                key, group, locale, CARD_BINDING, distanceFromUser(state, group.lat, group.lon),
                            ) + answer
                }
            }

            "SAVE" -> {
                val result = id?.let { sharingService.acceptPublished(userId, it) } ?: return null
                val text = when (result) {
                    is AcceptResult.Saved -> i18n.i18n("alerts.nearby.saved", locale, null, result.place.name)
                    AcceptResult.AlreadySaved -> i18n.i18n("alerts.shared.already_saved", locale)
                    AcceptResult.OwnPlace -> i18n.i18n("alerts.shared.own_place", locale)
                    AcceptResult.LimitReached -> i18n.i18n("alerts.add.limit", locale)
                    AcceptResult.NotFound -> i18n.i18n("alerts.place.not_found", locale)
                }
                listOf(AnswerCallbackAction(callbackQuery.id, text, showAlert = true))
            }

            "MAP" -> {
                val place = id?.let { savedPlaceService.get(userId, it) ?: nearbySearchService.findInUserGroups(userId, it) } ?: return null
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
        val result = nearbySearchService.searchOwn(userId, state.lat!!, state.lon!!, selected, includeGroups(userId), state.filter())
        val shown = result.points.take(MAX_SHOWN)
        val view = NearbyView(
            radiusText = formatter.distance(selected.meters, locale),
            points = shown.mapIndexed { index, point ->
                PointView(index + 1, point.place.name, formatter.distance(point.distanceMeters, locale), point.fromGroup)
            },
            hints = result.hints.map { HintView(formatter.distance(it.radius.meters, locale), it.extra, it.total) },
            nothingAnywhere = result.points.isEmpty() && result.hints.isEmpty(),
            hiddenCount = result.points.size - shown.size,
            hasGroup = shown.any { it.fromGroup },
            filterText = filterText(state, locale),
        )
        return key.buildMessage(
            step = NearbyStep.RESULT,
            model = view,
            inlineButtons = buttons(result, shown, state, locale),
            parseMode = FlowParseMode.HTML,
        )
    }

    private fun buttons(
        result: NearbyResult,
        shown: List<ru.grabovsky.poibot.geo.NearbyPoint>,
        state: NearbyState,
        locale: Locale,
    ): List<FlowInlineButton> {
        val buttons = mutableListOf<FlowInlineButton>()
        var row = 0
        shown.forEachIndexed { index, point ->
            val mark = if (point.fromGroup) "👥 " else ""
            val label = "${index + 1}. $mark${point.place.name.take(BUTTON_NAME_LENGTH)} · ${formatter.distance(point.distanceMeters, locale)}"
            buttons += FlowInlineButton(label, FlowCallbackPayload(key.value, "OPEN:${point.place.id}"), row++)
        }
        result.hints.forEach { hint ->
            val label = i18n.i18n("buttons.nearby.hint", locale, null, formatter.distance(hint.radius.meters, locale), hint.extra, hint.total)
            buttons += FlowInlineButton(label, FlowCallbackPayload(key.value, "R:${hint.radius.meters}"), row++)
        }
        buttons += FlowInlineButton(i18n.i18n("buttons.nearby.change_radius", locale), FlowCallbackPayload(key.value, "RADIUS"), row, 0)
        val filterLabel = filterText(state, locale)
            ?.let { i18n.i18n("buttons.nearby.filter_on", locale, null, it) }
            ?: i18n.i18n("buttons.nearby.filter", locale)
        buttons += FlowInlineButton(filterLabel, FlowCallbackPayload(key.value, "FILTER"), row, 1)
        return buttons
    }

    /** Статус и тег фильтра одной строкой («Был · #бар») или null, если фильтр выключен. */
    private fun filterText(state: NearbyState, locale: Locale): String? {
        val parts = listOfNotNull(
            PlaceStatus.fromCode(state.status)?.let { i18n.i18n("status.${it.name.lowercase()}", locale) },
            state.tag?.let { "#$it" },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun filterMessage(state: NearbyState, locale: Locale): FlowMessage {
        val buttons = mutableListOf<FlowInlineButton>()
        PlaceStatus.entries.forEachIndexed { index, status ->
            val text = i18n.i18n(PlaceStatus.buttonKey(status, status.name == state.status), locale)
            buttons += FlowInlineButton(text, FlowCallbackPayload(key.value, "FS:${status.name}"), 0, index)
        }
        state.tagChoices.forEachIndexed { index, tag ->
            buttons += FlowInlineButton(
                if (tag == state.tag) "✅ #$tag" else "#$tag",
                FlowCallbackPayload(key.value, "FT:$index"), 1 + index / 2, index % 2,
            )
        }
        var row = 1 + (state.tagChoices.size + 1) / 2
        if (state.filter().active) {
            buttons += FlowInlineButton(i18n.i18n("buttons.nearby.filter_reset", locale), FlowCallbackPayload(key.value, "FR"), row++, 0)
        }
        buttons += FlowInlineButton(i18n.i18n("buttons.common.back", locale), FlowCallbackPayload(key.value, "BACK"), row, 0)
        return key.buildMessage(
            step = NearbyStep.FILTER,
            model = FilterView(state.tagChoices.isNotEmpty(), filterText(state, locale)),
            inlineButtons = buttons,
            parseMode = FlowParseMode.HTML,
        )
    }

    /** Короткое сообщение, которое убирает reply-клавиатуру «Отправить геопозицию» и тут же исчезает. */
    private fun searchingMessage(): FlowMessage =
        FlowMessage(key, NearbyStep.SEARCHING.key, removeReplyKeyboard = true, autoDeleteAfterSeconds = SEARCH_VISIBLE_SECONDS)

    /** Аргумент запуска `LOC:<lat>:<lon>` - координаты, присланные боту без активного диалога. */
    private fun startLocation(args: String?): Pair<Double, Double>? {
        val parts = args?.takeIf { it.startsWith("LOC:") }?.split(':') ?: return null
        val lat = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
        val lon = parts.getOrNull(2)?.toDoubleOrNull() ?: return null
        return lat to lon
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

    private fun includeGroups(userId: Long): Boolean =
        userService.getUser(userId)?.profile?.settings?.nearbyGroups ?: true

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
        const val SEARCH_BINDING = "searching"
        const val SEARCH_VISIBLE_SECONDS = 1
        const val RESULT_BINDING = "result"
        const val CARD_BINDING = "card"
        const val MAX_SHOWN = 10
        const val BUTTON_NAME_LENGTH = 30
        const val MAX_TAG_CHOICES = 12
    }
}
