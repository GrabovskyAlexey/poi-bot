package ru.grabovsky.poibot.strategy.flow.core.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException
import org.springframework.stereotype.Component
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowPayloadSerializer

@Component
class JacksonFlowPayloadSerializer(
    private val objectMapper: ObjectMapper,
) : FlowPayloadSerializer {
    override fun <T : Any> deserialize(payload: String?, type: Class<T>): T {
        if (payload.isNullOrBlank()) {
            return defaultValue(type)
        }
        return try {
            objectMapper.readValue(payload, type)
        } catch (error: InvalidTypeIdException) {
            defaultValue(type)
        }
    }

    override fun <T : Any> serialize(payload: T?): String? {
        if (payload == null) {
            return null
        }
        return when (payload) {
            is Unit -> null
            else -> objectMapper.writeValueAsString(payload)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> defaultValue(type: Class<T>): T {
        return when {
            Unit::class.java.isAssignableFrom(type) -> Unit as T
            else -> objectMapper.readValue("{}", type)
        }
    }
}
