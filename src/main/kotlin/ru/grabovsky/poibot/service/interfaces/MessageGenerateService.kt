package ru.grabovsky.poibot.service.interfaces

import java.util.*

interface MessageGenerateService {
    fun processTemplate(template: String, freemarkerData: Any? = null, locale: Locale = Locale.forLanguageTag("ru")): String
}
