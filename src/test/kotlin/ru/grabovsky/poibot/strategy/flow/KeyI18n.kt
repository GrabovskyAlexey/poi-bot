package ru.grabovsky.poibot.strategy.flow

import ru.grabovsky.poibot.service.interfaces.I18nService
import java.util.*

/** Заглушка i18n для тестов: возвращает ключ, чтобы проверять, какое сообщение выбрано. */
class KeyI18n : I18nService {
    override fun i18n(code: String, locale: Locale, default: String?, vararg args: Any?) = code
}
