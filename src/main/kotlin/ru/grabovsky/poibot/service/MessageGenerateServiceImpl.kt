package ru.grabovsky.poibot.service

import org.springframework.stereotype.Service
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer
import ru.grabovsky.poibot.service.interfaces.MessageGenerateService
import java.io.StringWriter
import java.util.*

@Service
class MessageGenerateServiceImpl(
    private val freeMarkerConfigurer: FreeMarkerConfigurer,
) : MessageGenerateService {
    override fun processTemplate(template: String, freemarkerData: Any?, locale: Locale): String {
        val model = mutableMapOf<String, Any>("locale" to locale)
        if (freemarkerData != null) {
            model["data"] = freemarkerData
        }
        return processed(model, "$template.ftl", locale)
    }

    private fun processed(data: Map<String, Any>, templateName: String, locale: Locale): String {
        val template = freeMarkerConfigurer.configuration.getTemplate(templateName, locale)
        val output = StringWriter()
        template.process(data, output)
        return output.toString()
    }
}
