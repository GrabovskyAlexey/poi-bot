package ru.grabovsky.poibot.strategy.flow.core.engine

data class FlowMessage(
    val flowKey: FlowKey,
    val stepKey: String,
    val model: Any? = null,
    val inlineButtons: List<FlowInlineButton> = emptyList(),
    val replyButtons: List<FlowReplyButton> = emptyList(),
    val parseMode: FlowParseMode = FlowParseMode.MARKDOWN,
    val replyToMessageId: Int? = null,
    /** Убрать reply-клавиатуру, показанную ранее (используется, если нет своих кнопок). */
    val removeReplyKeyboard: Boolean = false,
    /** Удалить сообщение через N секунд после отправки/редактирования (временные подтверждения). */
    val autoDeleteAfterSeconds: Int? = null,
    /** Язык текста, если он должен отличаться от языка пользователя в контексте (например, сразу после смены языка). */
    val locale: java.util.Locale? = null,
)

data class FlowInlineButton(
    val text: String,
    val payload: FlowCallbackPayload,
    val row: Int = 0,
    val col: Int = 0,
    /** Если задан, кнопка открывает ссылку (например, диалог выбора чата Telegram), а payload игнорируется. */
    val url: String? = null,
) {
    companion object {
        /** Ряд «служебных» кнопок (закрыть, удалить, отмена): всегда внизу, сколько бы рядов ни добавили выше. */
        const val LAST_ROW = 99

        fun link(text: String, url: String, row: Int = 0, col: Int = 0) =
            FlowInlineButton(text, FlowCallbackPayload("", ""), row, col, url)
    }
}

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
