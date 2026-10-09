package ru.grabovsky.poibot.strategy.flow.core.templating

import org.springframework.stereotype.Component
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKey
import java.io.StringWriter
import java.util.*

@Component
class FlowTemplateRenderer(
    private val freeMarkerConfigurer: FreeMarkerConfigurer,
) {
    fun render(flowKey: FlowKey, stepKey: String, locale: Locale, data: Any? = null): String {
        val template = freeMarkerConfigurer.configuration.getTemplate(templatePath(flowKey, stepKey), locale)
        val model = mutableMapOf<String, Any>("locale" to locale)
        if (data != null) {
            model["data"] = data
        }
        val writer = StringWriter()
        template.process(model, writer)
        return writer.toString()
    }

    private fun templatePath(flowKey: FlowKey, stepKey: String): String {
        val flow = flowKey.value.lowercase(Locale.ROOT)
        val step = stepKey.lowercase(Locale.ROOT)
        return "$flow/$step.ftl"
    }
}
