package ru.grabovsky.poibot.service.interfaces

/** Скрытый по жалобам комментарий; автор администратору не показывается. */
data class HiddenComment(
    val id: Long,
    val text: String,
    val placeName: String,
    val placeAddress: String?,
    val reports: Long,
)

/** Модерация комментариев, скрытых по жалобам (раздел `/admin`). */
interface ModerationService {
    fun hiddenCount(): Long

    /** Скрытый комментарий с порядковым номером [index] (от старых к новым) или null, если такого номера нет. */
    fun hiddenAt(index: Int): HiddenComment?

    /** Снимает скрытие и сбрасывает жалобы, чтобы для повторного скрытия снова понадобилось нужное число жалоб. */
    fun restore(commentId: Long): Boolean

    /** Удаляет скрытый комментарий вместе с жалобами. */
    fun delete(commentId: Long): Boolean
}
