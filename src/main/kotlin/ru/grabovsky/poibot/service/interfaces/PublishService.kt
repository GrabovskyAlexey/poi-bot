package ru.grabovsky.poibot.service.interfaces

import ru.grabovsky.poibot.entity.Chat
import ru.grabovsky.poibot.entity.SavedPlace

class PublishNotAllowedException(message: String) : RuntimeException(message)

/** Публикация записей пользователя в группы и чтение опубликованного. */
interface PublishService {
    /**
     * Активные группы пользователя, где есть бот. Членство перепроверяется через Telegram:
     * ушедшие из группы отвязываются, при ошибке проверки запись сохраняется.
     */
    fun getUserGroups(userId: Long): List<Chat>

    /** chatId -> сколько из [placeIds] опубликовано в этом чате (чаты без публикаций не включаются). */
    fun publishedCounts(placeIds: Collection<Long>, chatIds: Collection<Long>): Map<Long, Int>

    /**
     * Публикует или снимает с публикации записи владельца в группе.
     * @return сколько записей изменилось
     * @throws PublishNotAllowedException если пользователь не связан с группой или бота в ней нет
     */
    fun setPublished(userId: Long, placeIds: Collection<Long>, chatId: Long, publish: Boolean): Int

    /**
     * Снять с публикации может владелец записи или администратор чата. Одно место, опубликованное в чате несколькими
     * участниками, в списке показывается один раз: администратор снимает все такие записи, участник - только свою.
     */
    fun unpublishAsModerator(userId: Long, chatId: Long, savedPlaceId: Long): Boolean

    fun canModerate(userId: Long, chatId: Long, place: SavedPlace): Boolean

    fun listPublished(chatId: Long, page: Int, pageSize: Int): SavedPlacePage

    fun getPublished(chatId: Long, savedPlaceId: Long): SavedPlace?
}
