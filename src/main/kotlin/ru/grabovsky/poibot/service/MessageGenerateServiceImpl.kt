package ru.grabovsky.poibot.service

import org.springframework.stereotype.Service
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer
import ru.grabovsky.poibot.service.interfaces.MessageGenerateService
import ru.grabovsky.poibot.strategy.state.StateCode
import java.io.StringWriter

@Service
class MessageGenerateServiceImpl(
    private val freeMarkerConfigurer: FreeMarkerConfigurer,
): MessageGenerateService {

    override fun process(state: StateCode, freemarkerData: Any?): String {
        val template = state.template ?: state.name.lowercase()
        return processed(freemarkerData?.let { mapOf("data" to it) }?: emptyMap(), "$template.ftl")
    }

    private fun processed(data: Map<String, Any>, templateName: String): String {
        val template = freeMarkerConfigurer.configuration.getTemplate(templateName)
        val output = StringWriter()
        template.process(data, output)
        return output.toString()
    }
}