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

/** Фото с подписью; текст сообщения (шаблон) уходит в caption (до 1024 символов). */
data class SendPhotoAction(
    override val bindingKey: String?,
    val photoFileId: String,
    val message: FlowMessage,
) : FlowAction

/** Показ места на карте (Telegram venue). */
data class SendVenueAction(
    override val bindingKey: String?,
    val title: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
) : FlowAction

/**
 * Перерисовывает уже отправленное сообщение-карточку по его id (когда id не хранится в привязках flow).
 * [caption] = true для карточек с фото (правится подпись, иначе текст). Ошибки правки не критичны.
 */
data class EditCardAction(
    val messageId: Int,
    val message: FlowMessage,
    val caption: Boolean,
) : FlowAction {
    override val bindingKey: String? = null
}

/** Файл в виде документа (например, выгрузка данных пользователя). */
data class SendDocumentAction(
    override val bindingKey: String?,
    val fileName: String,
    val content: ByteArray,
) : FlowAction
