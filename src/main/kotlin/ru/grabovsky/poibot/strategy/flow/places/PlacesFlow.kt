package ru.grabovsky.poibot.strategy.flow.places

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.service.interfaces.I18nService
import ru.grabovsky.poibot.service.interfaces.SavedPlaceService
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.core.support.buildMessage
import java.util.*

data class PlacesState(var page: Int = 0)

data class ListItemView(
    val index: Int,
    val name: String,
    val address: String?,
    val hasLocation: Boolean = false,
    val hasPhoto: Boolean = false,
)

/** Модель шаблона `places/list`. */
data class PlacesListView(val items: List<ListItemView>, val page: Int, val totalPages: Int, val total: Long)

/** Модель шаблона `places/confirm_delete`. */
data class ConfirmDeleteView(val name: String)

/** Список сохранённых точек пользователя с карточками, показом на карте и удалением. */
@Component
class PlacesFlow(
    private val savedPlaceService: SavedPlaceService,
    private val cardFactory: PlaceCardFactory,
    private val formatter: PlaceFormatter,
    private val i18n: I18nService,
) : FlowHandler<PlacesState> {

    override val key: FlowKey = FlowKeys.PLACES
    override val payloadType: Class<PlacesState> = PlacesState::class.java

    override fun start(context: FlowStartContext): FlowResult<PlacesState> {
        val state = PlacesState()
        return FlowResult(
            stepKey = PlacesStep.LIST.key,
            payload = state,
            actions = listOf(SendMessageAction(LIST_BINDING, listMessage(context.user.id, state, context.locale))),
        )
    }

    override fun onMessage(context: FlowContext<PlacesState>, message: Message): FlowResult<PlacesState>? = null

    override fun onCallback(
        context: FlowContext<PlacesState>,
        callbackQuery: CallbackQuery,
        data: String,
    ): FlowResult<PlacesState>? {
        val (command, argument) = parseCallback(data)
        val userId = callbackQuery.from.id
        val state = context.state.payload
        val locale = context.locale
        val id = argument?.toLongOrNull()
        val answer = AnswerCallbackAction(callbackQuery.id)

        val actions: List<FlowAction> = when (command) {
            "PAGE" -> {
                state.page = id?.toInt() ?: state.page
                listOf(EditMessageAction(LIST_BINDING, listMessage(userId, state, locale)), answer)
            }

            "OPEN" -> {
                val place = id?.let { savedPlaceService.get(userId, it) } ?: return notFound(state, callbackQuery, locale)
                listOf<FlowAction>(DeleteMessageAction(CARD_BINDING)) +
                        cardFactory.cardActions(key, place, locale, CARD_BINDING, manage = true) + answer
            }

            "MAP" -> {
                val place = id?.let { savedPlaceService.get(userId, it) } ?: return notFound(state, callbackQuery, locale)
                listOf(
                    SendVenueAction(null, place.name, place.address, place.lat ?: return null, place.lon ?: return null),
                    answer,
                )
            }

            "CLOSE" -> listOf(DeleteMessageAction(CARD_BINDING), answer)

            "DELASK" -> {
                val place = id?.let { savedPlaceService.get(userId, it) } ?: return notFound(state, callbackQuery, locale)
                listOf(DeleteMessageAction(CARD_BINDING), SendMessageAction(CARD_BINDING, confirmDeleteMessage(place.id!!, place.name, locale)), answer)
            }

            "DELOK" -> {
                val deleted = id?.let { savedPlaceService.delete(userId, it) } ?: false
                listOf(
                    DeleteMessageAction(CARD_BINDING),
                    EditMessageAction(LIST_BINDING, listMessage(userId, state, locale)),
                    AnswerCallbackAction(
                        callbackQuery.id,
                        i18n.i18n(if (deleted) "alerts.places.deleted" else "alerts.place.not_found", locale),
                    ),
                )
            }

            else -> return null
        }
        return FlowResult(PlacesStep.LIST.key, state, actions)
    }

    private fun notFound(state: PlacesState, callbackQuery: CallbackQuery, locale: Locale): FlowResult<PlacesState> =
        FlowResult(
            PlacesStep.LIST.key, state,
            listOf(AnswerCallbackAction(callbackQuery.id, i18n.i18n("alerts.place.not_found", locale), showAlert = true)),
        )

    private fun listMessage(userId: Long, state: PlacesState, locale: Locale): FlowMessage {
        val page = savedPlaceService.list(userId, state.page, PAGE_SIZE)
        state.page = page.page
        val items = page.items.mapIndexed { index, place ->
            ListItemView(
                page.page * PAGE_SIZE + index + 1, place.name, formatter.shorten(place.address, ADDRESS_PREVIEW),
                hasLocation = place.hasLocation(), hasPhoto = place.photoFileId != null,
            )
        }
        val buttons = mutableListOf<FlowInlineButton>()
        page.items.forEachIndexed { index, place ->
            val label = "${items[index].index}. ${place.name.take(BUTTON_NAME_LENGTH)}"
            buttons += FlowInlineButton(label, FlowCallbackPayload(key.value, "OPEN:${place.id}"), row = index)
        }
        if (page.totalPages > 1) {
            val navRow = page.items.size
            if (page.page > 0) {
                buttons += FlowInlineButton("◀", FlowCallbackPayload(key.value, "PAGE:${page.page - 1}"), navRow, 0)
            }
            if (page.page < page.totalPages - 1) {
                buttons += FlowInlineButton("▶", FlowCallbackPayload(key.value, "PAGE:${page.page + 1}"), navRow, 1)
            }
        }
        if (page.totalItems > 0) {
            buttons += FlowInlineButton(
                i18n.i18n("buttons.places.publish_many", locale),
                FlowCallbackPayload(FlowKeys.PUBLISH.value, "ALL"),
                row = page.items.size + (if (page.totalPages > 1) 1 else 0),
            )
        }
        return key.buildMessage(
            step = PlacesStep.LIST,
            model = PlacesListView(items, page.page + 1, page.totalPages, page.totalItems),
            inlineButtons = buttons,
            parseMode = FlowParseMode.HTML,
        )
    }

    private fun confirmDeleteMessage(id: Long, name: String, locale: Locale): FlowMessage =
        key.buildMessage(
            step = PlacesStep.CONFIRM_DELETE,
            model = ConfirmDeleteView(name),
            inlineButtons = listOf(
                FlowInlineButton(i18n.i18n("buttons.places.delete_yes", locale), FlowCallbackPayload(key.value, "DELOK:$id"), 0, 0),
                FlowInlineButton(i18n.i18n("buttons.common.cancel", locale), FlowCallbackPayload(key.value, "OPEN:$id"), 0, 1),
            ),
            parseMode = FlowParseMode.HTML,
        )

    private companion object {
        const val LIST_BINDING = "list"
        const val CARD_BINDING = "card"
        const val PAGE_SIZE = 8
        const val BUTTON_NAME_LENGTH = 40
        const val ADDRESS_PREVIEW = 60
    }
}
