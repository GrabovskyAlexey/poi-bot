package ru.grabovsky.poibot.service

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.I18nService
import java.util.*

@Component
class I18nServiceImpl(
    private val messageSource: MessageSource,
) : I18nService {
    override fun i18n(
        code: String,
        locale: Locale,
        default: String?,
        vararg args: Any?
    ): String {
        val arguments = if (args.isEmpty()) null else Array(args.size) { args[it] ?: "" }
        return messageSource.getMessage(code, arguments, default ?: code, locale)!!
    }
}