package ru.grabovsky.poibot.strategy.flow.publish

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.service.interfaces.I18nService
import ru.grabovsky.poibot.service.interfaces.PublishNotAllowedException
import ru.grabovsky.poibot.service.interfaces.PublishService
import ru.grabovsky.poibot.service.interfaces.SavedPlaceService
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.core.support.buildMessage
import java.util.*

enum class PublishStep(override val key: String) : FlowStep {
    SELECT("select"),
    GROUPS("groups"),
    PICK("pick"),
    DONE("done"),
}

data class GroupDto(var id: Long = 0, var title: String = "")

data class PublishState(
    var mode: String = MODE_SELECT,
    var single: Boolean = false,
    var page: Int = 0,
    var selected: MutableList<Long> = mutableListOf(),
    var groups: MutableList<GroupDto> = mutableListOf(),
) {
    companion object {
        const val MODE_SELECT = "select"
        const val MODE_GROUPS = "groups"
    }
}

data class SelectItemView(val index: Int, val name: String, val selected: Boolean)

/** Модель шаблона `publish/select`. */
data class SelectView(val items: List<SelectItemView>, val page: Int, val totalPages: Int, val total: Long, val selectedCount: Int)

data class GroupItemView(val title: String, val state: String, val published: Int)

/** Модель шаблона `publish/groups`. */
data class GroupsView(val placeNames: List<String>, val moreCount: Int, val groups: List<GroupItemView>)

/**
 * Публикация своих мест в группы. Либо одно место (из карточки), либо несколько (выбор из списка).
 * Список групп пользователя собирается кнопкой Telegram «выбрать чат» (сообщение chat_shared).
 */
@Component
class PublishFlow(
    private val savedPlaceService: SavedPlaceService,
    private val publishService: PublishService,
    private val i18n: I18nService,
) : FlowHandler<PublishState> {

    override val key: FlowKey = FlowKeys.PUBLISH
    override val payloadType: Class<PublishState> = PublishState::class.java

    override fun start(context: FlowStartContext): FlowResult<PublishState> {
        val userId = context.user.id
        val locale = context.locale
        val state = PublishState()
        val oneId = oneIdFrom(context.args)
        if (oneId != null && savedPlaceService.get(userId, oneId) != null) {
            enterGroups(state, userId, listOf(oneId), single = true)
            return FlowResult(
                PublishStep.GROUPS.key, state,
                listOf(
                    SendMessageAction(MAIN_BINDING, groupsMessage(userId, state, locale)),
                    SendMessageAction(PICK_BINDING, pickMessage(locale)),
                ),
            )
        }
        return FlowResult(
            PublishStep.SELECT.key, state,
            listOf(SendMessageAction(MAIN_BINDING, selectMessage(userId, state, locale))),
        )
    }

    override fun onMessage(context: FlowContext<PublishState>, message: Message): FlowResult<PublishState>? {
        val shared = message.chatShared ?: return null
        val state = context.state.payload
        if (state.mode != PublishState.MODE_GROUPS) return null
        val userId = message.from.id
        // Чат уже зарегистрирован и привязан к пользователю в ReceiverService
        refreshGroups(state, userId)
        logger.debug { "User $userId shared chat ${shared.chatId} in publish flow" }
        return FlowResult(
            PublishStep.GROUPS.key, state,
            listOf(
                DeleteMessageIdAction(message.messageId),
                EditMessageAction(MAIN_BINDING, groupsMessage(userId, state, context.locale)),
            ),
        )
    }

    override fun onCallback(
        context: FlowContext<PublishState>,
        callbackQuery: CallbackQuery,
        data: String,
    ): FlowResult<PublishState>? {
        val (command, argument) = parseCallback(data)
        val userId = callbackQuery.from.id
        val locale = context.locale
        val state = context.state.payload
        val answer = AnswerCallbackAction(callbackQuery.id)

        val actions: List<FlowAction> = when (command) {
            "ALL" -> {
                if (state.mode == PublishState.MODE_SELECT && state.selected.isEmpty() && state.page == 0 && !state.single) {
                    listOf(answer)
                } else {
                    resetToSelect(state)
                    listOf(DeleteMessageAction(MAIN_BINDING), DeleteMessageAction(PICK_BINDING)) +
                            SendMessageAction(MAIN_BINDING, selectMessage(userId, state, locale)) + answer
                }
            }

            "ONE" -> {
                val id = argument?.toLongOrNull() ?: return null
                if (state.mode == PublishState.MODE_GROUPS && state.single && state.selected == listOf(id)) {
                    listOf(answer)
                } else {
                    savedPlaceService.get(userId, id) ?: return notFound(state, callbackQuery, locale)
                    enterGroups(state, userId, listOf(id), single = true)
                    listOf(DeleteMessageAction(MAIN_BINDING), DeleteMessageAction(PICK_BINDING)) +
                            SendMessageAction(MAIN_BINDING, groupsMessage(userId, state, locale)) +
                            SendMessageAction(PICK_BINDING, pickMessage(locale)) + answer
                }
            }

            "S" -> {
                val id = argument?.toLongOrNull() ?: return null
                if (!state.selected.remove(id)) state.selected.add(id)
                listOf(EditMessageAction(MAIN_BINDING, selectMessage(userId, state, locale)), answer)
            }

            "PAGE" -> {
                state.page = argument?.toIntOrNull() ?: state.page
                listOf(EditMessageAction(MAIN_BINDING, selectMessage(userId, state, locale)), answer)
            }

            "NEXT" -> {
                if (state.selected.isEmpty()) {
                    return alertResult(state, callbackQuery, "alerts.publish.nothing_selected", locale)
                }
                enterGroups(state, userId, state.selected.toList(), single = false)
                listOf(
                    EditMessageAction(MAIN_BINDING, groupsMessage(userId, state, locale)),
                    SendMessageAction(PICK_BINDING, pickMessage(locale)),
                    answer,
                )
            }

            "BACK" -> {
                state.mode = PublishState.MODE_SELECT
                listOf(
                    DeleteMessageAction(PICK_BINDING),
                    EditMessageAction(MAIN_BINDING, selectMessage(userId, state, locale)),
                    answer,
                )
            }

            "G" -> {
                val chatId = argument?.toLongOrNull() ?: return null
                toggleGroup(state, userId, chatId, callbackQuery, locale)
                    ?: return alertResult(state, callbackQuery, "alerts.publish.not_allowed", locale)
            }

            "CANCEL" -> {
                return FlowResult(
                    PublishStep.SELECT.key, state,
                    listOf(DeleteMessageAction(MAIN_BINDING), DeleteMessageAction(PICK_BINDING), answer),
                    completed = true,
                )
            }

            "DONE" -> {
                return FlowResult(
                    PublishStep.DONE.key, state,
                    listOf(
                        DeleteMessageAction(MAIN_BINDING),
                        DeleteMessageAction(PICK_BINDING),
                        SendMessageAction(null, doneMessage()),
                        answer,
                    ),
                    completed = true,
                )
            }

            else -> return null
        }
        return FlowResult(stepFor(state), state, actions)
    }

    // --- логика ------------------------------------------------------------------------------

    private fun resetToSelect(state: PublishState) {
        state.mode = PublishState.MODE_SELECT
        state.single = false
        state.page = 0
        state.selected.clear()
    }

    private fun enterGroups(state: PublishState, userId: Long, placeIds: List<Long>, single: Boolean) {
        state.mode = PublishState.MODE_GROUPS
        state.single = single
        state.selected = placeIds.toMutableList()
        refreshGroups(state, userId)
    }

    private fun refreshGroups(state: PublishState, userId: Long) {
        state.groups = publishService.getUserGroups(userId)
            .map { GroupDto(it.id, it.title ?: it.id.toString()) }
            .toMutableList()
    }

    /** @return действия или null, если публикация в этот чат недоступна */
    private fun toggleGroup(
        state: PublishState,
        userId: Long,
        chatId: Long,
        callbackQuery: CallbackQuery,
        locale: Locale,
    ): List<FlowAction>? {
        if (state.groups.none { it.id == chatId }) return null
        val published = publishService.publishedCounts(state.selected, listOf(chatId))[chatId] ?: 0
        val publish = published < state.selected.size
        try {
            publishService.setPublished(userId, state.selected, chatId, publish)
        } catch (error: PublishNotAllowedException) {
            logger.info { "Publish refused: ${error.message}" }
            refreshGroups(state, userId)
            return null
        }
        return listOf(
            EditMessageAction(MAIN_BINDING, groupsMessage(userId, state, locale)),
            AnswerCallbackAction(callbackQuery.id),
        )
    }

    private fun stepFor(state: PublishState) =
        if (state.mode == PublishState.MODE_GROUPS) PublishStep.GROUPS.key else PublishStep.SELECT.key

    private fun notFound(state: PublishState, callbackQuery: CallbackQuery, locale: Locale) =
        alertResult(state, callbackQuery, "alerts.place.not_found", locale)

    private fun alertResult(state: PublishState, callbackQuery: CallbackQuery, textKey: String, locale: Locale) =
        FlowResult(
            stepFor(state), state,
            listOf(AnswerCallbackAction(callbackQuery.id, i18n.i18n(textKey, locale), showAlert = true)),
        )

    private fun oneIdFrom(args: String?): Long? =
        args?.takeIf { it.startsWith("ONE:") }?.removePrefix("ONE:")?.toLongOrNull()

    // --- сообщения ---------------------------------------------------------------------------

    private fun selectMessage(userId: Long, state: PublishState, locale: Locale): FlowMessage {
        val page = savedPlaceService.list(userId, state.page, PAGE_SIZE)
        state.page = page.page
        val items = page.items.mapIndexed { index, place ->
            SelectItemView(page.page * PAGE_SIZE + index + 1, place.name, place.id in state.selected)
        }
        val buttons = mutableListOf<FlowInlineButton>()
        page.items.forEachIndexed { index, place ->
            val mark = if (place.id in state.selected) "☑" else "⬜"
            val label = "$mark ${items[index].index}. ${place.name.take(BUTTON_NAME_LENGTH)}"
            buttons += FlowInlineButton(label, payload("S:${place.id}"), row = index)
        }
        var row = page.items.size
        if (page.totalPages > 1) {
            if (page.page > 0) buttons += FlowInlineButton("◀", payload("PAGE:${page.page - 1}"), row, 0)
            if (page.page < page.totalPages - 1) buttons += FlowInlineButton("▶", payload("PAGE:${page.page + 1}"), row, 1)
            row++
        }
        if (page.totalItems > 0) {
            buttons += FlowInlineButton(
                i18n.i18n("buttons.publish.next", locale, null, state.selected.size), payload("NEXT"), row++,
            )
        }
        buttons += FlowInlineButton(i18n.i18n("buttons.common.cancel", locale), payload("CANCEL"), row)
        return key.buildMessage(
            step = PublishStep.SELECT,
            model = SelectView(items, page.page + 1, page.totalPages, page.totalItems, state.selected.size),
            inlineButtons = buttons,
            parseMode = FlowParseMode.HTML,
        )
    }

    private fun groupsMessage(userId: Long, state: PublishState, locale: Locale): FlowMessage {
        val counts = publishService.publishedCounts(state.selected, state.groups.map { it.id })
        val total = state.selected.size
        val groupViews = state.groups.map { group ->
            val published = counts[group.id] ?: 0
            val groupState = when {
                published == 0 -> "none"
                published >= total -> "all"
                else -> "some"
            }
            GroupItemView(group.title, groupState, published)
        }
        val names = state.selected.mapNotNull { savedPlaceService.get(userId, it)?.name }
        val buttons = mutableListOf<FlowInlineButton>()
        var row = 0
        state.groups.forEachIndexed { index, group ->
            val view = groupViews[index]
            val label = when (view.state) {
                "all" -> "✅ ${group.title.take(BUTTON_NAME_LENGTH)}"
                "some" -> "➖ ${group.title.take(BUTTON_NAME_LENGTH)} (${view.published}/$total)"
                else -> "⬜ ${group.title.take(BUTTON_NAME_LENGTH)}"
            }
            buttons += FlowInlineButton(label, payload("G:${group.id}"), row++)
        }
        if (!state.single) {
            buttons += FlowInlineButton(i18n.i18n("buttons.common.back", locale), payload("BACK"), row, 0)
            buttons += FlowInlineButton(i18n.i18n("buttons.publish.done", locale), payload("DONE"), row, 1)
        } else {
            buttons += FlowInlineButton(i18n.i18n("buttons.publish.done", locale), payload("DONE"), row)
        }
        return key.buildMessage(
            step = PublishStep.GROUPS,
            model = GroupsView(
                placeNames = names.take(MAX_NAMES_SHOWN),
                moreCount = (names.size - MAX_NAMES_SHOWN).coerceAtLeast(0),
                groups = groupViews,
            ),
            inlineButtons = buttons,
            parseMode = FlowParseMode.HTML,
        )
    }

    private fun pickMessage(locale: Locale): FlowMessage =
        key.buildMessage(
            step = PublishStep.PICK,
            replyButtons = listOf(
                FlowReplyButton(i18n.i18n("buttons.publish.pick_group", locale), requestChatId = PICK_REQUEST_ID),
            ),
            parseMode = FlowParseMode.HTML,
        )

    private fun doneMessage(): FlowMessage =
        FlowMessage(
            key, PublishStep.DONE.key, parseMode = FlowParseMode.HTML,
            removeReplyKeyboard = true, autoDeleteAfterSeconds = DONE_VISIBLE_SECONDS,
        )

    private fun payload(data: String) = FlowCallbackPayload(key.value, data)

    private companion object {
        val logger = KotlinLogging.logger {}
        const val MAIN_BINDING = "main"
        const val PICK_BINDING = "pick"
        const val PICK_REQUEST_ID = "1"
        const val PAGE_SIZE = 8
        const val BUTTON_NAME_LENGTH = 35
        const val MAX_NAMES_SHOWN = 5
        const val DONE_VISIBLE_SECONDS = 3
    }
}
