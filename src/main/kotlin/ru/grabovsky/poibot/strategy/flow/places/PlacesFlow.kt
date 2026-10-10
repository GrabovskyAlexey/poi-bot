package ru.grabovsky.poibot.strategy.flow.places

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.entity.PlaceStatus
import ru.grabovsky.poibot.service.interfaces.I18nService
import ru.grabovsky.poibot.service.interfaces.PlaceListPage
import ru.grabovsky.poibot.service.interfaces.PlaceListQuery
import ru.grabovsky.poibot.service.interfaces.PlaceListService
import ru.grabovsky.poibot.service.interfaces.PlaceSort
import ru.grabovsky.poibot.service.interfaces.SavedPlaceService
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.core.support.buildMessage
import java.util.*

data class PlacesState(
    var page: Int = 0,
    var query: String? = null,
    var sort: String = PlaceSort.NEW.name,
    var withLocation: Boolean = false,
    var withPhoto: Boolean = false,
    var tag: String? = null,
    /** Фильтр по личному статусу ([ru.grabovsky.poibot.entity.PlaceStatus.name]). */
    var status: String? = null,
    /** Теги, предложенные в выборе фильтра (кнопки несут индекс из-за лимита callback-данных). */
    var tagChoices: MutableList<String> = mutableListOf(),
    /** Ждём текст поиска от пользователя. */
    var awaitingSearch: Boolean = false,
) {
    fun toQuery() = PlaceListQuery(query, PlaceSort.valueOf(sort), withLocation, withPhoto, tag, status)
}

data class ListItemView(
    val index: Int,
    val name: String,
    val address: String?,
    val hasLocation: Boolean = false,
    val hasPhoto: Boolean = false,
    /** «4.3 (12)»; null, если оценок нет. */
    val rating: String? = null,
)

/**
 * Модель шаблонов `places/list` и `group/list`. [filtered] - включён поиск или фильтры (пустой список тогда значит
 * «ничего не найдено», а не «мест нет»); [sortName] - название сортировки, если она не по умолчанию.
 */
data class PlacesListView(
    val items: List<ListItemView>,
    val page: Int,
    val totalPages: Int,
    val total: Long,
    val filtered: Boolean = false,
    val query: String? = null,
    val withLocation: Boolean = false,
    val withPhoto: Boolean = false,
    val sortName: String? = null,
    val tag: String? = null,
    val statusText: String? = null,
)

/** Модель шаблона `places/confirm_delete`. */
data class ConfirmDeleteView(val name: String)

/** Список сохранённых точек пользователя с карточками, показом на карте и удалением. */
@Component
class PlacesFlow(
    private val savedPlaceService: SavedPlaceService,
    private val placeListService: PlaceListService,
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

    /** Текст поиска: принимается, только пока бот его ждёт (после нажатия «Поиск»). */
    override fun onMessage(context: FlowContext<PlacesState>, message: Message): FlowResult<PlacesState>? {
        val state = context.state.payload
        if (!state.awaitingSearch) return null
        val text = message.text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        state.query = text.take(MAX_QUERY_LENGTH)
        state.page = 0
        state.awaitingSearch = false
        val actions = listOf(
            DeleteMessageIdAction(message.messageId),
            DeleteMessageAction(PROMPT_BINDING),
            EditMessageAction(LIST_BINDING, listMessage(message.from.id, state, context.locale)),
        )
        return FlowResult(PlacesStep.LIST.key, state, actions)
    }

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

            "SEARCH" -> {
                state.awaitingSearch = true
                listOf(
                    DeleteMessageAction(PROMPT_BINDING),
                    SendMessageAction(PROMPT_BINDING, searchPromptMessage(locale)),
                    answer,
                )
            }

            "SCANCEL" -> {
                state.awaitingSearch = false
                listOf(DeleteMessageAction(PROMPT_BINDING), answer)
            }

            "RESET" -> {
                state.query = null
                state.withLocation = false
                state.withPhoto = false
                state.tag = null
                state.status = null
                state.page = 0
                listOf(EditMessageAction(LIST_BINDING, listMessage(userId, state, locale)), answer)
            }

            "FSTAT" -> {
                val target = PlaceStatus.fromCode(argument) ?: return null
                state.status = PlaceStatus.toggle(PlaceStatus.fromCode(state.status), target)?.name
                state.page = 0
                listOf(EditMessageAction(LIST_BINDING, listMessage(userId, state, locale)), answer)
            }

            "FTAG" -> {
                state.tagChoices = savedPlaceService.popularTags(userId).take(MAX_TAG_CHOICES).toMutableList()
                if (state.tagChoices.isEmpty()) {
                    listOf(answer)
                } else {
                    listOf(
                        DeleteMessageAction(PROMPT_BINDING),
                        SendMessageAction(PROMPT_BINDING, tagChoiceMessage(state, locale)),
                        answer,
                    )
                }
            }

            "TAGF" -> {
                val tag = argument?.toIntOrNull()?.let { state.tagChoices.getOrNull(it) }
                    ?: return notFound(state, callbackQuery, locale)
                state.tag = tag.takeIf { it != state.tag }
                state.page = 0
                listOf(
                    DeleteMessageAction(PROMPT_BINDING),
                    EditMessageAction(LIST_BINDING, listMessage(userId, state, locale)),
                    answer,
                )
            }

            "FLOC" -> {
                state.withLocation = !state.withLocation
                state.page = 0
                listOf(EditMessageAction(LIST_BINDING, listMessage(userId, state, locale)), answer)
            }

            "FPHOTO" -> {
                state.withPhoto = !state.withPhoto
                state.page = 0
                listOf(EditMessageAction(LIST_BINDING, listMessage(userId, state, locale)), answer)
            }

            "SORT" -> {
                state.sort = nextSort(PlaceSort.valueOf(state.sort)).name
                state.page = 0
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

            "STATUS" -> {
                val parts = argument?.split(':')
                val target = PlaceStatus.fromCode(parts?.getOrNull(1)) ?: return null
                val place = parts?.getOrNull(0)?.toLongOrNull()?.let { savedPlaceService.toggleStatus(userId, it, target) }
                    ?: return notFound(state, callbackQuery, locale)
                val cardId = callbackQuery.message?.messageId ?: return null
                // Личный статус виден в карточке и меняется списком фильтров, поэтому обновляем карточку на месте
                listOf(cardFactory.refreshAction(CardRef(cardId, place.id!!, PlaceCardFactory.OWNER_PLACES), place, locale), answer)
            }

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
        val query = state.toQuery()
        val page: PlaceListPage = placeListService.searchOwn(userId, query, state.page, PAGE_SIZE)
        state.page = page.page
        val items = page.items.mapIndexed { index, entry ->
            val place = entry.place
            ListItemView(
                page.page * PAGE_SIZE + index + 1, place.name, formatter.shorten(place.address, ADDRESS_PREVIEW),
                hasLocation = place.hasLocation(), hasPhoto = place.photoFileId != null,
                rating = formatter.rating(entry.rating),
            )
        }
        val buttons = mutableListOf<FlowInlineButton>()
        page.items.forEachIndexed { index, entry ->
            val label = "${items[index].index}. ${entry.place.name.take(BUTTON_NAME_LENGTH)}"
            buttons += FlowInlineButton(label, FlowCallbackPayload(key.value, "OPEN:${entry.place.id}"), row = index)
        }
        var row = page.items.size
        if (page.totalPages > 1) {
            if (page.page > 0) {
                buttons += FlowInlineButton("◀", FlowCallbackPayload(key.value, "PAGE:${page.page - 1}"), row, 0)
            }
            if (page.page < page.totalPages - 1) {
                buttons += FlowInlineButton("▶", FlowCallbackPayload(key.value, "PAGE:${page.page + 1}"), row, 1)
            }
            row++
        }
        if (page.totalUnfiltered > 0) {
            buttons += control("buttons.places.search", locale, "SEARCH", row, 0)
            buttons += control("buttons.places.sort.${query.sort.name.lowercase()}", locale, "SORT", row, 1)
            row++
            buttons += control(filterKey("buttons.places.filter_location", query.withLocation), locale, "FLOC", row, 0)
            buttons += control(filterKey("buttons.places.filter_photo", query.withPhoto), locale, "FPHOTO", row, 1)
            row++
            PlaceStatus.entries.forEachIndexed { index, status ->
                buttons += control(PlaceStatus.buttonKey(status, status.name == query.status), locale, "FSTAT:${status.name}", row, index)
            }
            row++
            if (page.tags.isNotEmpty()) {
                val tagLabel = query.tag?.let { i18n.i18n("buttons.places.filter_tag_on", locale, null, it) }
                    ?: i18n.i18n("buttons.places.filter_tag", locale)
                buttons += FlowInlineButton(tagLabel, FlowCallbackPayload(key.value, "FTAG"), row++, 0)
            }
            if (query.filtered) {
                buttons += control("buttons.places.reset", locale, "RESET", row++, 0)
            }
            buttons += FlowInlineButton(
                i18n.i18n("buttons.places.publish_many", locale),
                FlowCallbackPayload(FlowKeys.PUBLISH.value, "ALL"),
                row = row,
            )
        }
        return key.buildMessage(
            step = PlacesStep.LIST,
            model = PlacesListView(
                items, page.page + 1, page.totalPages, page.total,
                filtered = query.filtered, query = query.text?.trim()?.takeIf { it.isNotEmpty() },
                withLocation = query.withLocation, withPhoto = query.withPhoto,
                tag = query.tag,
                statusText = PlaceStatus.fromCode(query.status)?.let { i18n.i18n("status.${it.name.lowercase()}", locale) },
                sortName = query.sort.takeIf { it != PlaceSort.NEW }
                    ?.let { i18n.i18n("places.sort_name.${it.name.lowercase()}", locale) },
            ),
            inlineButtons = buttons,
            parseMode = FlowParseMode.HTML,
        )
    }

    private fun tagChoiceMessage(state: PlacesState, locale: Locale): FlowMessage {
        val buttons = state.tagChoices.mapIndexed { index, tag ->
            FlowInlineButton(if (tag == state.tag) "✅ #$tag" else "#$tag", FlowCallbackPayload(key.value, "TAGF:$index"), index / 2, index % 2)
        } + control("buttons.common.cancel", locale, "SCANCEL", state.tagChoices.size / 2 + 1, 0)
        return key.buildMessage(step = PlacesStep.TAG_PROMPT, inlineButtons = buttons, parseMode = FlowParseMode.HTML)
    }

    private fun searchPromptMessage(locale: Locale): FlowMessage =
        key.buildMessage(
            step = PlacesStep.SEARCH_PROMPT,
            inlineButtons = listOf(control("buttons.common.cancel", locale, "SCANCEL", 0, 0)),
            parseMode = FlowParseMode.HTML,
        )

    private fun control(textKey: String, locale: Locale, data: String, row: Int, col: Int) =
        FlowInlineButton(i18n.i18n(textKey, locale), FlowCallbackPayload(key.value, data), row, col)

    /** Включённый фильтр помечается ключом с суффиксом _on. */
    private fun filterKey(base: String, active: Boolean) = if (active) base + "_on" else base

    private fun nextSort(current: PlaceSort): PlaceSort =
        PlaceSort.entries[(current.ordinal + 1) % PlaceSort.entries.size]

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
        const val PROMPT_BINDING = "prompt"
        const val MAX_TAG_CHOICES = 12
        const val MAX_QUERY_LENGTH = 50
        const val PAGE_SIZE = 8
        const val BUTTON_NAME_LENGTH = 40
        const val ADDRESS_PREVIEW = 60
    }
}
