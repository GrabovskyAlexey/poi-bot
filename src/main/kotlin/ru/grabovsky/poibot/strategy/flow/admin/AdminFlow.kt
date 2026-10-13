package ru.grabovsky.poibot.strategy.flow.admin

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.service.interfaces.HiddenComment
import ru.grabovsky.poibot.service.interfaces.I18nService
import ru.grabovsky.poibot.service.interfaces.ModerationService
import ru.grabovsky.poibot.service.interfaces.StatsService
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.stats.STATS_ZONE
import ru.grabovsky.poibot.stats.StatsSnapshot
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.core.support.buildMessage
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

enum class AdminStep(override val key: String) : FlowStep {
    MAIN("main"),
    HIDDEN("hidden"),
}

/** Состояния нет: статистика считается при каждом открытии, а номер комментария несут сами кнопки. */
data class AdminState(var unused: Boolean = false)

/** Модель шаблона `admin/main`: числа уже готовы к показу, недели - моноширинной таблицей. */
data class AdminView(
    val generatedAt: String,
    val stats: StatsSnapshot,
    val withPlacesPercent: Int,
    val weekTable: String,
)

/** Модель шаблона `admin/hidden`: комментарий номер [position] из [total] скрытых. */
data class HiddenCommentView(
    val position: Int,
    val total: Long,
    val comment: HiddenComment,
)

/**
 * `/admin`: статистика бота и модерация скрытых по жалобам комментариев. Права проверяются при запуске и в каждом
 * callback-е. Одно сообщение переключается между статистикой и просмотром скрытых комментариев по одному.
 */
@Component
class AdminFlow(
    private val statsService: StatsService,
    private val moderationService: ModerationService,
    private val userService: UserService,
    private val i18n: I18nService,
) : FlowHandler<AdminState> {

    override val key: FlowKey = FlowKeys.ADMIN
    override val payloadType: Class<AdminState> = AdminState::class.java

    override fun start(context: FlowStartContext): FlowResult<AdminState> {
        if (!isAdmin(context.user.id)) return FlowResult(AdminStep.MAIN.key, AdminState(), emptyList(), completed = true)
        return FlowResult(
            AdminStep.MAIN.key, AdminState(),
            listOf(SendMessageAction(MAIN_BINDING, mainMessage(context.locale))),
        )
    }

    override fun onMessage(context: FlowContext<AdminState>, message: Message): FlowResult<AdminState>? = null

    override fun onCallback(
        context: FlowContext<AdminState>,
        callbackQuery: CallbackQuery,
        data: String,
    ): FlowResult<AdminState>? {
        if (!isAdmin(callbackQuery.from.id)) return null
        val state = context.state.payload
        val locale = context.locale
        val answer = AnswerCallbackAction(callbackQuery.id)
        val (command, argument) = parseCallback(data)
        return when (command) {
            "REFRESH", "BACK" -> show(state, AdminStep.MAIN, mainMessage(locale), answer)
            "HID" -> showHidden(state, locale, callbackQuery, argument?.toIntOrNull() ?: 0)
            "RESTORE" -> moderate(state, locale, callbackQuery, argument) { moderationService.restore(it) }
            "DELETE" -> moderate(state, locale, callbackQuery, argument) { moderationService.delete(it) }
            "CLOSE" -> FlowResult(
                AdminStep.MAIN.key, state, listOf(DeleteMessageAction(MAIN_BINDING), answer), completed = true,
            )
            else -> null
        }
    }

    /** Просмотр скрытого комментария с номером [index]; за последним снова первый, пустой список - уведомление. */
    private fun showHidden(
        state: AdminState,
        locale: Locale,
        callbackQuery: CallbackQuery,
        index: Int,
    ): FlowResult<AdminState> {
        val total = moderationService.hiddenCount()
        if (total == 0L) {
            return show(
                state, AdminStep.MAIN, mainMessage(locale),
                AnswerCallbackAction(callbackQuery.id, i18n.i18n("alerts.admin.no_hidden", locale), showAlert = true),
            )
        }
        val position = if (index in 0 until total) index else 0
        val comment = moderationService.hiddenAt(position)
            ?: return show(state, AdminStep.MAIN, mainMessage(locale), AnswerCallbackAction(callbackQuery.id))
        return show(state, AdminStep.HIDDEN, hiddenMessage(comment, position, total, locale), AnswerCallbackAction(callbackQuery.id))
    }

    /** Применяет решение к комментарию `<id>:<номер>` и показывает следующий (на том же номере) или статистику. */
    private fun moderate(
        state: AdminState,
        locale: Locale,
        callbackQuery: CallbackQuery,
        argument: String?,
        action: (Long) -> Boolean,
    ): FlowResult<AdminState>? {
        val parts = argument?.split(':') ?: return null
        val commentId = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val index = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val done = action(commentId)
        val total = moderationService.hiddenCount()
        val textKey = if (done) "alerts.admin.moderated" else "alerts.admin.comment_gone"
        val answer = AnswerCallbackAction(callbackQuery.id, i18n.i18n(textKey, locale))
        if (total == 0L) return show(state, AdminStep.MAIN, mainMessage(locale), answer)
        val position = if (index in 0 until total) index else 0
        val next = moderationService.hiddenAt(position) ?: return show(state, AdminStep.MAIN, mainMessage(locale), answer)
        return show(state, AdminStep.HIDDEN, hiddenMessage(next, position, total, locale), answer)
    }

    private fun show(state: AdminState, step: AdminStep, message: FlowMessage, answer: AnswerCallbackAction) =
        FlowResult(step.key, state, listOf(EditMessageAction(MAIN_BINDING, message), answer))

    private fun isAdmin(userId: Long) = userService.getUser(userId)?.profile?.isAdmin == true

    private fun mainMessage(locale: Locale, now: Instant = Instant.now()): FlowMessage {
        val stats = statsService.snapshot(now)
        val withPlaces = if (stats.audience.total == 0L) 0 else (stats.audience.withPlaces * 100 / stats.audience.total).toInt()
        val view = AdminView(
            generatedAt = TIME.format(now.atZone(STATS_ZONE)),
            stats = stats,
            withPlacesPercent = withPlaces,
            weekTable = weekTable(stats),
        )
        return key.buildMessage(
            step = AdminStep.MAIN,
            model = view,
            // Кнопка модерации есть, только если есть что модерировать: так новые жалобы сразу заметны
            inlineButtons = listOfNotNull(
                button(i18n.i18n("buttons.admin.hidden", locale, null, stats.content.hiddenComments), "HID:0", 0)
                    .takeIf { stats.content.hiddenComments > 0 },
                button(i18n.i18n("buttons.admin.refresh", locale), "REFRESH", 1),
                button(i18n.i18n("buttons.places.close", locale), "CLOSE", FlowInlineButton.LAST_ROW),
            ),
            parseMode = FlowParseMode.HTML,
        )
    }

    private fun hiddenMessage(comment: HiddenComment, index: Int, total: Long, locale: Locale): FlowMessage =
        key.buildMessage(
            step = AdminStep.HIDDEN,
            model = HiddenCommentView(index + 1, total, comment),
            inlineButtons = listOf(
                button(i18n.i18n("buttons.admin.restore", locale), "RESTORE:${comment.id}:$index", 0, 0),
                button(i18n.i18n("buttons.admin.delete", locale), "DELETE:${comment.id}:$index", 0, 1),
                button(i18n.i18n("buttons.admin.next", locale), "HID:${index + 1}", 1, 0),
                button(i18n.i18n("buttons.admin.back", locale), "BACK", 1, 1),
            ),
            parseMode = FlowParseMode.HTML,
        )

    private fun weekTable(stats: StatsSnapshot): String {
        val header = String.format("%-6s %5s %7s %5s", "нед.", "новых", "активн.", "мест")
        val rows = stats.weeks.map {
            String.format("%-6s %5d %7d %5d", WEEK.format(it.weekStart), it.newUsers, it.activeUsers, it.newPlaces)
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun button(text: String, data: String, row: Int, col: Int = 0) =
        FlowInlineButton(text, FlowCallbackPayload(key.value, data), row, col)

    private companion object {
        const val MAIN_BINDING = "main"
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        val WEEK: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
    }
}
