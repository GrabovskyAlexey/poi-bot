package ru.grabovsky.poibot.service.interfaces

import java.util.*


interface I18nService {
    fun i18n(code: String, locale: Locale, default: String? = null, vararg args: Any?): String
}