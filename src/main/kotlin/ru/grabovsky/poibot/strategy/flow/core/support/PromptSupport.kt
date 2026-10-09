package ru.grabovsky.poibot.strategy.flow.core.support

import java.util.UUID
import ru.grabovsky.poibot.strategy.flow.core.engine.AnswerCallbackAction
import ru.grabovsky.poibot.strategy.flow.core.engine.DeleteMessageAction
import ru.grabovsky.poibot.strategy.flow.core.engine.DeleteMessageIdAction
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowAction
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowContext
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowInlineButton
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKey
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowMessage
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowResult
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowStep
import ru.grabovsky.poibot.strategy.flow.core.engine.SendMessageAction
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowCallbackPayload
import org.telegram.telegrambots.meta.api.objects.CallbackQuery

/**
 * Маркерное состояние с поддержкой prompt-сообщений.
 */
interface PromptState {
    val promptBindings: MutableList<String>
}

private typealias ActionAppender = MutableList<FlowAction>.() -> Unit

object PromptSupport {
    fun nextBinding(prefix: String): String = "${prefix}_${UUID.randomUUID()}"
}

fun FlowKey.cancelPromptButton(text: String): FlowInlineButton =
    FlowInlineButton(
        text = text,
        payload = FlowCallbackPayload(value, "PROMPT:CANCEL"),
        row = 0,
        col = 0
    )

fun PromptState.cleanupPromptMessages(): MutableList<FlowAction> {
    val actions = promptBindings.fold(mutableListOf<FlowAction>()) { acc, binding ->
        acc += DeleteMessageAction(binding)
        acc
    }
    promptBindings.clear()
    return actions
}

fun <S : PromptState> FlowContext<S>.startPrompt(
    targetStep: FlowStep,
    bindingPrefix: String,
    callbackQuery: CallbackQuery,
    updateState: S.() -> Unit = {},
    appendActions: ActionAppender = {},
    buildMessage: (binding: String) -> FlowMessage,
): FlowResult<S> =
    withPromptBinding(bindingPrefix, updateState) { state, binding ->
        val actions = mutableListOf<FlowAction>()
        actions += SendMessageAction(
            bindingKey = binding,
            message = buildMessage(binding)
        )
        actions.appendActions()
        actions += AnswerCallbackAction(callbackQuery.id)

        FlowResult(
            stepKey = targetStep.key,
            payload = state,
            actions = actions
        )
    }

fun <S : PromptState> FlowContext<S>.retryPrompt(
    targetStep: FlowStep,
    bindingPrefix: String,
    userMessageId: Int?,
    updateState: S.() -> Unit = {},
    appendActions: ActionAppender = {},
    buildMessage: (binding: String) -> FlowMessage,
): FlowResult<S> =
    withPromptBinding(bindingPrefix, updateState) { state, binding ->
        val actions = mutableListOf<FlowAction>()
        actions += SendMessageAction(
            bindingKey = binding,
            message = buildMessage(binding)
        )
        if (userMessageId != null) {
            actions += DeleteMessageIdAction(userMessageId)
        }
        actions.appendActions()

        FlowResult(
            stepKey = targetStep.key,
            payload = state,
            actions = actions
        )
    }

fun <S : PromptState> FlowContext<S>.finalizePrompt(
    targetStep: FlowStep,
    userMessageId: Int?,
    updateState: S.() -> Unit = {},
    appendActions: ActionAppender = {},
): FlowResult<S> {
    val state = this.state.payload
    val actions = state.cleanupPromptMessages()
    if (userMessageId != null) {
        actions += DeleteMessageIdAction(userMessageId)
    }
    state.updateState()
    actions.appendActions()

    return FlowResult(
        stepKey = targetStep.key,
        payload = state,
        actions = actions
    )
}

fun <S : PromptState> FlowContext<S>.cancelPrompt(
    targetStep: FlowStep,
    callbackQuery: CallbackQuery,
    updateState: S.() -> Unit = {},
    appendActions: ActionAppender = {},
): FlowResult<S> {
    val state = this.state.payload
    val actions = state.cleanupPromptMessages()
    state.updateState()
    actions.appendActions()
    actions += AnswerCallbackAction(callbackQuery.id)

    return FlowResult(
        stepKey = targetStep.key,
        payload = state,
        actions = actions
    )
}

private inline fun <S : PromptState> FlowContext<S>.withPromptBinding(
    prefix: String,
    updateState: S.() -> Unit,
    block: (S, String) -> FlowResult<S>
): FlowResult<S> {
    val state = this.state.payload
    val binding = PromptSupport.nextBinding(prefix)
    state.promptBindings.add(binding)
    state.updateState()
    return block(state, binding)
}