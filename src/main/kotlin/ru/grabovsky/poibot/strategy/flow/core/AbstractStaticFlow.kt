package ru.grabovsky.poibot.strategy.flow.core

import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowContext
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowHandler
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKey
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowResult
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowStartContext
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowStep
import ru.grabovsky.poibot.strategy.flow.core.engine.SendMessageAction
import ru.grabovsky.poibot.strategy.flow.core.support.buildMessage

/**
 * Базовая реализация для простых флоу, которые просто отправляют статический экран.
 */
abstract class AbstractStaticFlow(
    override val key: FlowKey,
    private val step: FlowStep,
    private val bindingKey: String,
    private val modelProvider: (FlowStartContext) -> Any?,
) : FlowHandler<Unit> {

    override val payloadType: Class<Unit> = Unit::class.java

    override fun start(context: FlowStartContext): FlowResult<Unit> {
        val model = modelProvider(context)
        return FlowResult(
            stepKey = step.key,
            payload = Unit,
            actions = listOf(
                SendMessageAction(
                    bindingKey = bindingKey,
                    message = key.buildMessage(
                        step = step,
                        model = model
                    )
                )
            )
        )
    }

    override fun onMessage(context: FlowContext<Unit>, message: Message): FlowResult<Unit>? = null

    override fun onCallback(
        context: FlowContext<Unit>,
        callbackQuery: CallbackQuery,
        data: String
    ): FlowResult<Unit>? = null
}
