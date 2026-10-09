package ru.grabovsky.poibot.strategy.flow.core.engine

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.service.interfaces.FlowStateService
import java.util.*

@Component
class FlowEngine(
    handlers: List<FlowHandler<out Any>>,
    private val stateService: FlowStateService,
    private val serializer: FlowPayloadSerializer,
    private val actionExecutor: FlowActionExecutor,
) {
    private val handlerByKey: Map<FlowKey, FlowHandler<out Any>> = handlers.associateBy { it.key }

    fun start(flowKey: FlowKey, user: User, locale: Locale, args: String? = null): Boolean {
        val handler = handler<Any>(flowKey) ?: return false
        stateService.clear(user.id, flowKey)
        val result = handler.start(FlowStartContext(user, locale, args))
        applyResult(user, flowKey, null, result, locale)
        return true
    }

    fun onMessage(flowKey: FlowKey, user: User, locale: Locale, message: Message): Boolean {
        val handler = handler<Any>(flowKey)
        if (handler == null) {
            logger.debug { "FlowEngine.onMessage: handler not found for flow='${flowKey.value}', userId=${user.id}" }
            return false
        }

        val snapshot = stateService.load(user.id, flowKey)
        if (snapshot == null) {
            logger.debug { "FlowEngine.onMessage: snapshot not found for flow='${flowKey.value}', userId=${user.id}" }
            return false
        }

        val context = FlowContext(
            user = user,
            locale = locale,
            state = snapshot.toStateHolder(handler),
        )
        logger.debug { "FlowEngine.onMessage: calling handler for flow='${flowKey.value}', userId=${user.id}, messageId=${message.messageId}, text='${message.text?.take(50)}', step='${snapshot.stepKey}'" }
        val result = handler.onMessage(context, message)
        if (result == null) {
            logger.debug { "FlowEngine.onMessage: handler returned null for flow='${flowKey.value}', userId=${user.id}" }
            return false
        }
        applyResult(user, flowKey, snapshot, result, context.locale)
        return true
    }

    fun onCallback(flowKey: FlowKey, user: User, locale: Locale, callbackQuery: CallbackQuery, data: String): Boolean {
        val handler = handler<Any>(flowKey)
        if (handler == null) {
            logger.debug { "FlowEngine.onCallback: handler not found for flow='${flowKey.value}', userId=${user.id}" }
            return false
        }

        val snapshot = stateService.load(user.id, flowKey)
        if (snapshot == null) {
            logger.debug { "FlowEngine.onCallback: snapshot not found for flow='${flowKey.value}', userId=${user.id}" }
            return false
        }

        val context = FlowContext(
            user = user,
            locale = locale,
            state = snapshot.toStateHolder(handler),
        )
        logger.debug { "FlowEngine.onCallback: calling handler for flow='${flowKey.value}', userId=${user.id}, data='$data', step='${snapshot.stepKey}'" }
        val result = handler.onCallback(context, callbackQuery, data)
        if (result == null) {
            logger.debug { "FlowEngine.onCallback: handler returned null for flow='${flowKey.value}', userId=${user.id}, data='$data'" }
            return false
        }
        applyResult(user, flowKey, snapshot, result, context.locale)
        return true
    }

    private fun FlowStateSnapshot.toMutableBindings(): MutableMap<String, Int> =
        messageBindings.toMutableMap()

    private fun <TPayload : Any> FlowStateSnapshot.toStateHolder(handler: FlowHandler<TPayload>): FlowStateHolder<TPayload> {
        val payload = serializer.deserialize(payload, handler.payloadType)
        return FlowStateHolder(
            stepKey = stepKey,
            payload = payload,
            messageBindings = messageBindings,
        )
    }

    private fun <TPayload : Any> applyResult(
        user: User,
        flowKey: FlowKey,
        previousSnapshot: FlowStateSnapshot?,
        result: FlowResult<TPayload>,
        locale: Locale,
    ) {
        val currentBindings = previousSnapshot?.messageBindings ?: emptyMap()
        val mutation = actionExecutor.execute(user, locale, currentBindings, result.actions)
        val updatedBindings = currentBindings
            .toMutableMap()
            .apply {
                putAll(mutation.replacements)
                mutation.removed.forEach { remove(it) }
            }

        if (result.completed) {
            stateService.clear(user.id, flowKey)
            return
        }

        val payloadJson = serializer.serialize(result.payload)
        val snapshot = FlowStateSnapshot(
            userId = user.id,
            flowKey = flowKey,
            stepKey = result.stepKey,
            payload = payloadJson,
            messageBindings = updatedBindings,
        )
        stateService.save(snapshot)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <TPayload : Any> handler(flowKey: FlowKey): FlowHandler<TPayload>? =
        handlerByKey[flowKey] as? FlowHandler<TPayload>

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}