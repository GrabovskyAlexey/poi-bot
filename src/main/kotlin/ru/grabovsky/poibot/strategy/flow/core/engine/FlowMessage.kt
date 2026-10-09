package ru.grabovsky.poibot.strategy.flow.core.engine

data class FlowMessage(
    val flowKey: FlowKey,
    val stepKey: String,
    val model: Any? = null,
    val inlineButtons: List<FlowInlineButton> = emptyList(),
    val replyButtons: List<FlowReplyButton> = emptyList(),
    val parseMode: FlowParseMode = FlowParseMode.MARKDOWN,
    val replyToMessageId: Int? = null,
)

data class FlowInlineButton(
    val text: String,
    val payload: FlowCallbackPayload,
    val row: Int = 0,
    val col: Int = 0,
)

data class FlowReplyButton(
    val text: String,
    val requestLocation: Boolean = false,
    /** requestId системного выбора чата (KeyboardButtonRequestChat); результат придёт сообщением chat_shared. */
    val requestChatId: String? = null,
)

enum class FlowParseMode(val telegramValue: String?) {
    MARKDOWN("Markdown"),
    HTML("HTML"),
    NONE(null),
}

data class FlowCallbackPayload(
    val flow: String,
    val data: String,
)
