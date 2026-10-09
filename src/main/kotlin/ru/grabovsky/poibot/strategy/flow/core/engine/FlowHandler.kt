package ru.grabovsky.poibot.strategy.flow.core.engine

import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.message.Message
import java.util.*

interface FlowHandler<TPayload : Any> {
    val key: FlowKey
    val payloadType: Class<TPayload>

    fun start(context: FlowStartContext): FlowResult<TPayload>

    fun onMessage(context: FlowContext<TPayload>, message: Message): FlowResult<TPayload>?

    fun onCallback(context: FlowContext<TPayload>, callbackQuery: CallbackQuery, data: String): FlowResult<TPayload>?

    fun parseCallback(data: String): Pair<String, String?> =
        if (data.contains(':')) {
            val split = data.split(':', limit = 2)
            split[0] to split[1]
        } else {
            data to null
        }
}

data class FlowStartContext(
    val user: User,
    val locale: Locale,
)

data class FlowStateHolder<TPayload : Any>(
    val stepKey: String,
    val payload: TPayload,
    val messageBindings: Map<String, Int>,
)

data class FlowContext<TPayload : Any>(
    val user: User,
    var locale: Locale,
    val state: FlowStateHolder<TPayload>,
)

data class FlowResult<TPayload : Any>(
    val stepKey: String,
    val payload: TPayload,
    val actions: List<FlowAction> = emptyList(),
    val completed: Boolean = false,
)
