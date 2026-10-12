package ru.grabovsky.poibot.strategy.flow.admin

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.service.interfaces.I18nService
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
}

/** Состояния нет: статистика считается при каждом открытии и обновлении. */
data class AdminState(var unused: Boolean = false)

/** Модель шаблона `admin/main`: числа уже готовы к показу, недели - моноширинной таблицей. */
data class AdminView(
    val generatedAt: String,
    val stats: StatsSnapshot,
    val withPlacesPercent: Int,
    val weekTable: String,
)

/** `/admin`: статистика бота для администратора. Все проверки прав повторяются в callback-ах. */
@Component
class AdminFlow(
    private val statsService: StatsService,
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
        val answer = AnswerCallbackAction(callbackQuery.id)
        return when (parseCallback(data).first) {
            "REFRESH" -> FlowResult(
                AdminStep.MAIN.key, state, listOf(EditMessageAction(MAIN_BINDING, mainMessage(context.locale)), answer),
            )
            "CLOSE" -> FlowResult(
                AdminStep.MAIN.key, state, listOf(DeleteMessageAction(MAIN_BINDING), answer), completed = true,
            )
            else -> null
        }
    }

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
            inlineButtons = listOf(
                FlowInlineButton(i18n.i18n("buttons.admin.refresh", locale), FlowCallbackPayload(key.value, "REFRESH"), 0),
                FlowInlineButton(i18n.i18n("buttons.places.close", locale), FlowCallbackPayload(key.value, "CLOSE"), FlowInlineButton.LAST_ROW),
            ),
            parseMode = FlowParseMode.HTML,
        )
    }

    private fun weekTable(stats: StatsSnapshot): String {
        val header = String.format("%-6s %5s %7s %5s", "нед.", "новых", "активн.", "мест")
        val rows = stats.weeks.map {
            String.format("%-6s %5d %7d %5d", WEEK.format(it.weekStart), it.newUsers, it.activeUsers, it.newPlaces)
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private companion object {
        const val MAIN_BINDING = "main"
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        val WEEK: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
    }
}
