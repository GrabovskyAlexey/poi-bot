package ru.grabovsky.poibot.strategy.flow.settings

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.entity.User
import ru.grabovsky.poibot.geo.SearchRadius
import ru.grabovsky.poibot.service.interfaces.I18nService
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.core.support.buildMessage
import ru.grabovsky.poibot.util.LocaleUtils
import java.util.*

enum class SettingsStep(override val key: String) : FlowStep {
    MAIN("main"),
}

/** Состояния нет: значения читаются из профиля при каждой перерисовке. */
data class SettingsState(var unused: Boolean = false)

/**
 * `/settings`: язык интерфейса, радиус `/nearby` по умолчанию и удаление служебных сообщений. Каждая кнопка
 * переключает значение по кругу и перерисовывает то же сообщение (язык - уже на новом языке).
 */
@Component
class SettingsFlow(
    private val userService: UserService,
    private val i18n: I18nService,
) : FlowHandler<SettingsState> {

    override val key: FlowKey = FlowKeys.SETTINGS
    override val payloadType: Class<SettingsState> = SettingsState::class.java

    override fun start(context: FlowStartContext): FlowResult<SettingsState> {
        val user = userService.getUser(context.user.id)
        return FlowResult(
            SettingsStep.MAIN.key, SettingsState(),
            listOf(SendMessageAction(MAIN_BINDING, mainMessage(user, context.locale))),
        )
    }

    override fun onMessage(context: FlowContext<SettingsState>, message: Message): FlowResult<SettingsState>? = null

    override fun onCallback(
        context: FlowContext<SettingsState>,
        callbackQuery: CallbackQuery,
        data: String,
    ): FlowResult<SettingsState>? {
        val (command, _) = parseCallback(data)
        val state = context.state.payload
        val answer = AnswerCallbackAction(callbackQuery.id)
        if (command == "CLOSE") {
            return FlowResult(SettingsStep.MAIN.key, state, listOf(DeleteMessageAction(MAIN_BINDING), answer), completed = true)
        }
        val user = userService.getUser(callbackQuery.from.id) ?: return null
        val profile = user.profile ?: return null
        when (command) {
            "LANG" -> profile.locale = nextLanguage(profile.locale)
            "RADIUS" -> profile.settings.searchRadiusMeters = nextRadius(profile.settings.searchRadiusMeters)
            "CLEAN" -> profile.settings.cleanChat = !profile.settings.cleanChat
            else -> return null
        }
        userService.saveUser(user)
        // Язык мог поменяться: перерисовываем уже на новом
        val locale = LocaleUtils.resolve(user)
        return FlowResult(SettingsStep.MAIN.key, state, listOf(EditMessageAction(MAIN_BINDING, mainMessage(user, locale)), answer))
    }

    private fun mainMessage(user: User?, locale: Locale): FlowMessage {
        val settings = user?.profile?.settings
        val language = user?.profile?.locale
        val radius = SearchRadius.fromMeters(settings?.searchRadiusMeters).meters
        val clean = settings?.cleanChat ?: true
        return key.buildMessage(
            step = SettingsStep.MAIN,
            inlineButtons = listOf(
                button(i18n.i18n("buttons.settings.language", locale, null, i18n.i18n("settings.language.${language ?: "auto"}", locale)), "LANG", 0),
                button(i18n.i18n("buttons.settings.radius", locale, null, radius.toString()), "RADIUS", 1),
                button(i18n.i18n("buttons.settings.clean.${if (clean) "on" else "off"}", locale), "CLEAN", 2),
                button(i18n.i18n("buttons.places.close", locale), "CLOSE", FlowInlineButton.LAST_ROW),
            ),
            parseMode = FlowParseMode.HTML,
        // Текст рендерим на выбранном языке: после его смены язык в контексте ещё прежний
        ).copy(locale = locale)
    }

    private fun button(text: String, data: String, row: Int) =
        FlowInlineButton(text, FlowCallbackPayload(key.value, data), row)

    companion object {
        const val MAIN_BINDING = "main"
        private val LANGUAGES = listOf(null, "ru", "en")

        /** Язык по кругу: авто (язык Telegram) -> ru -> en -> авто. */
        fun nextLanguage(current: String?): String? =
            LANGUAGES[(LANGUAGES.indexOf(current).coerceAtLeast(0) + 1) % LANGUAGES.size]

        fun nextRadius(currentMeters: Int): Int {
            val radii = SearchRadius.entries
            return radii[(radii.indexOf(SearchRadius.fromMeters(currentMeters)) + 1) % radii.size].meters
        }
    }
}
