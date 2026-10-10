package ru.grabovsky.poibot.service.interfaces

import ru.grabovsky.poibot.entity.SavedPlace
import java.time.Instant

sealed interface AcceptResult {
    /** Копия записи создана у получателя. */
    data class Saved(val place: SavedPlace) : AcceptResult

    /** У получателя уже есть запись об этом месте. */
    data object AlreadySaved : AcceptResult

    /** Получатель — владелец исходной записи. */
    data object OwnPlace : AcceptResult

    /** Ссылка недействительна (запись удалена или токен неверный). */
    data object NotFound : AcceptResult

    data object LimitReached : AcceptResult
}

/** Шаринг записей между пользователями по ссылке-токену. */
interface SharingService {
    /**
     * Токен ссылки на запись владельца (создаётся один раз и переиспользуется, пока не истёк срок или ссылку
     * не отозвали; новая ссылка без срока); null, если записи нет.
     */
    fun getOrCreateToken(ownerId: Long, savedPlaceId: Long): String?

    /** Запись по токену; null, если ссылка недействительна (неверный токен, срок вышел, ссылку отозвали). */
    fun findShared(token: String): SavedPlace?

    /** Когда истекает ссылка; null - без срока или ссылки нет. */
    fun expiresAt(token: String): Instant?

    /** Срок действия ссылки владельца считая от сейчас; [days] = null - без срока. false, если ссылки нет или она чужая. */
    fun setExpiry(ownerId: Long, token: String, days: Int?): Boolean

    /** Отзывает ссылку владельца: прежний токен перестаёт работать. false, если ссылки нет или она чужая. */
    fun revoke(ownerId: Long, token: String): Boolean

    /**
     * Сохраняет копию записи получателю. Копия привязана к тому же месту ([SavedPlace.placeId]),
     * поэтому рейтинг и комментарии будут общими; название, описание и фото копируются как стартовые значения.
     */
    fun accept(userId: Long, token: String): AcceptResult

    companion object {
        /** Префикс параметра deep link: `/start sp_<token>`. */
        const val START_PREFIX = "sp_"
    }
}
