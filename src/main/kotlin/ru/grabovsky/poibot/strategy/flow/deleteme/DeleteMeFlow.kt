package ru.grabovsky.poibot.strategy.flow.deleteme

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.service.interfaces.I18nService
import ru.grabovsky.poibot.service.interfaces.UserDataService
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.core.support.buildMessage
import java.util.*

enum class DeleteMeStep(override val key: String) : FlowStep {
    MENU("menu"),
    CONFIRM("confirm"),
    DONE("done"),
}

/** Состояния нет: шаг (меню или подтверждение) хранится в самом flow. */
data class DeleteMeState(var unused: Boolean = false)

/**
 * `/deleteme`: выгрузка данных файлом и полное удаление с подтверждением. Удаление выполняется только
 * на шаге подтверждения, поэтому старая кнопка из прежнего сообщения ничего не удалит.
 */
@Component
class DeleteMeFlow(
    private val userDataService: UserDataService,
    private val i18n: I18nService,
) : FlowHandler<DeleteMeState> {

    override val key: FlowKey = FlowKeys.DELETE_ME
    override val payloadType: Class<DeleteMeState> = DeleteMeState::class.java

    override fun start(context: FlowStartContext): FlowResult<DeleteMeState> =
        FlowResult(
            DeleteMeStep.MENU.key, DeleteMeState(),
            listOf(SendMessageAction(MAIN_BINDING, menuMessage(context.locale))),
        )

    override fun onMessage(context: FlowContext<DeleteMeState>, message: Message): FlowResult<DeleteMeState>? = null

    override fun onCallback(
        context: FlowContext<DeleteMeState>,
        callbackQuery: CallbackQuery,
        data: String,
    ): FlowResult<DeleteMeState>? {
        val (command, _) = parseCallback(data)
        val state = context.state.payload
        val locale = context.locale
        val userId = callbackQuery.from.id
        val answer = AnswerCallbackAction(callbackQuery.id)
        return when (command) {
            "EXPORT" -> FlowResult(
                context.state.stepKey, state,
                listOf(SendDocumentAction(null, EXPORT_FILE_NAME, userDataService.export(userId)), answer),
            )

            "ASK" -> FlowResult(
                DeleteMeStep.CONFIRM.key, state,
                listOf(EditMessageAction(MAIN_BINDING, confirmMessage(locale)), answer),
            )

            "BACK" -> FlowResult(
                DeleteMeStep.MENU.key, state,
                listOf(EditMessageAction(MAIN_BINDING, menuMessage(locale)), answer),
            )

            "DO" -> {
                if (context.state.stepKey != DeleteMeStep.CONFIRM.key) {
                    return FlowResult(
                        context.state.stepKey, state,
                        listOf(AnswerCallbackAction(callbackQuery.id, i18n.i18n("alerts.deleteme.outdated", locale), showAlert = true)),
                    )
                }
                userDataService.deleteAll(userId)
                logger.info { "User $userId deleted all own data" }
                FlowResult(
                    DeleteMeStep.DONE.key, state,
                    listOf(EditMessageAction(MAIN_BINDING, key.buildMessage(DeleteMeStep.DONE, parseMode = FlowParseMode.HTML)), answer),
                    completed = true,
                )
            }

            "CLOSE" -> FlowResult(
                DeleteMeStep.MENU.key, state,
                listOf(DeleteMessageAction(MAIN_BINDING), answer),
                completed = true,
            )

            else -> null
        }
    }

    private fun menuMessage(locale: Locale): FlowMessage =
        key.buildMessage(
            step = DeleteMeStep.MENU,
            inlineButtons = listOf(
                button("buttons.deleteme.export", locale, "EXPORT", 0),
                button("buttons.deleteme.ask", locale, "ASK", 1),
                button("buttons.places.close", locale, "CLOSE", FlowInlineButton.LAST_ROW),
            ),
            parseMode = FlowParseMode.HTML,
        )

    private fun confirmMessage(locale: Locale): FlowMessage =
        key.buildMessage(
            step = DeleteMeStep.CONFIRM,
            inlineButtons = listOf(
                button("buttons.deleteme.confirm", locale, "DO", 0),
                button("buttons.common.cancel", locale, "BACK", FlowInlineButton.LAST_ROW),
            ),
            parseMode = FlowParseMode.HTML,
        )

    private fun button(textKey: String, locale: Locale, data: String, row: Int) =
        FlowInlineButton(i18n.i18n(textKey, locale), FlowCallbackPayload(key.value, data), row)

    private companion object {
        val logger = KotlinLogging.logger {}
        const val MAIN_BINDING = "main"
        const val EXPORT_FILE_NAME = "poibot-data.json"
    }
}
