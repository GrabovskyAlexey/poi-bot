package ru.grabovsky.poibot.service.interfaces

import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.message.Message

/**
 * Команды чтения в группах: список опубликованных мест и поиск рядом. Без состояния и без flow-движка,
 * чтобы не засорять группу: каждый ответ — одно сообщение, листание правит то же сообщение.
 */
interface GroupPlacesService {
    fun showPlaces(chat: Chat, user: User, replyToMessageId: Int?)

    /** Просит ответить на сообщение геопозицией. */
    fun askNearbyLocation(chat: Chat, user: User, replyToMessageId: Int?)

    /** Подсказка, что места добавляются в личке с ботом. */
    fun showAddHint(chat: Chat, user: User, replyToMessageId: Int?)

    /** Обрабатывает сообщение в группе; true, если это ответ с геопозицией на запрос бота. */
    fun handleMessage(message: Message): Boolean

    /** @param data часть callback-данных после ключа [CALLBACK_KEY] */
    fun onCallback(callbackQuery: CallbackQuery, data: String)

    companion object {
        /** Значение поля `flow` в callback-данных, по которому ReceiverService отличает групповые кнопки. */
        const val CALLBACK_KEY = "GP"
    }
}
