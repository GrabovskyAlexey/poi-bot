package ru.grabovsky.poibot.strategy.flow.core.support

import ru.grabovsky.poibot.strategy.flow.core.engine.*

fun FlowKey.buildMessage(
    step: FlowStep,
    model: Any? = null,
    inlineButtons: List<FlowInlineButton> = emptyList(),
    replyButtons: List<FlowReplyButton> = emptyList(),
    parseMode: FlowParseMode = FlowParseMode.MARKDOWN,
    autoDeleteAfterSeconds: Int? = null,
): FlowMessage = FlowMessage(
    flowKey = this,
    stepKey = step.key,
    model = model,
    inlineButtons = inlineButtons,
    replyButtons = replyButtons,
    parseMode = parseMode,
    autoDeleteAfterSeconds = autoDeleteAfterSeconds,
)
