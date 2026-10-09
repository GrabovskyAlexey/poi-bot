package ru.grabovsky.poibot.service.interfaces

import ru.grabovsky.poibot.entity.SavedPlace

/** Данные записи, собранные в форме добавления/редактирования. */
data class SavedPlaceDraft(
    val name: String,
    val address: String? = null,
    val description: String? = null,
    val websiteUrl: String? = null,
    val photoFileId: String? = null,
    val photoFileUniqueId: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)

data class SavedPlacePage(val items: List<SavedPlace>, val page: Int, val totalPages: Int, val totalItems: Long)

class PlaceLimitExceededException(val limit: Int) : RuntimeException("Place limit $limit exceeded")

interface SavedPlaceService {
    /**
     * Создаёт запись. [linkPlaceId] — подтверждённое пользователем существующее место;
     * если не задано, создаётся новое [ru.grabovsky.poibot.entity.Place].
     */
    fun create(ownerId: Long, draft: SavedPlaceDraft, linkPlaceId: Long? = null): SavedPlace

    /** Обновляет запись владельца; null, если записи нет. */
    fun update(ownerId: Long, id: Long, draft: SavedPlaceDraft, linkPlaceId: Long? = null): SavedPlace?

    fun get(ownerId: Long, id: Long): SavedPlace?

    fun list(ownerId: Long, page: Int, pageSize: Int): SavedPlacePage

    fun delete(ownerId: Long, id: Long): Boolean

    companion object {
        const val MAX_PLACES_PER_USER = 500
    }
}
