package ru.grabovsky.poibot.strategy.flow.core.engine

sealed interface FlowAction {
    val bindingKey: String?
}

data class SendMessageAction(
    override val bindingKey: String?,
    val message: FlowMessage,
) : FlowAction

data class EditMessageAction(
    override val bindingKey: String,
    val message: FlowMessage,
) : FlowAction

data class DeleteMessageAction(
    override val bindingKey: String,
) : FlowAction

data class DeleteMessageIdAction(
    val messageId: Int,
) : FlowAction {
    override val bindingKey: String? = null
}

data class AnswerCallbackAction(
    val callbackQueryId: String,
    val text: String? = null,
    val showAlert: Boolean = false,
) : FlowAction {
    override val bindingKey: String? = null
}

data class SetReactionAction(
    val chatId: Long,
    val messageId: Int,
    val emoji: String,
) : FlowAction {
    override val bindingKey: String? = null
}
