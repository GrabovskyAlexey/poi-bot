package ru.grabovsky.poibot.service.interfaces

import ru.grabovsky.poibot.entity.SavedPlace

enum class PlaceSort { NEW, RATING, NAME }

/** Условия списка своих мест: поиск по названию и адресу, фильтры и сортировка. */
data class PlaceListQuery(
    val text: String? = null,
    val sort: PlaceSort = PlaceSort.NEW,
    val withLocation: Boolean = false,
    val withPhoto: Boolean = false,
    /** Только места с этим тегом. */
    val tag: String? = null,
    /** Только места с этим личным статусом ([ru.grabovsky.poibot.entity.PlaceStatus.name]). */
    val status: String? = null,
) {
    /** Есть ли поиск или фильтры (сортировка условием не считается). */
    val filtered: Boolean get() = !text.isNullOrBlank() || withLocation || withPhoto || tag != null || status != null
}

data class PlaceListEntry(val place: SavedPlace, val rating: RatingSummary)

/** Страница списка; [totalUnfiltered] - сколько мест всего без учёта поиска и фильтров. */
data class PlaceListPage(
    val items: List<PlaceListEntry>,
    val page: Int,
    val totalPages: Int,
    val total: Long,
    val totalUnfiltered: Long,
    /** Теги всех мест пользователя от частых к редким (для фильтра по тегу); для групп пусто. */
    val tags: List<String> = emptyList(),
)

interface PlaceListService {
    /** Свои места пользователя с поиском, фильтрами и сортировкой. */
    fun searchOwn(ownerId: Long, query: PlaceListQuery, page: Int, pageSize: Int): PlaceListPage

    /** Места, опубликованные в чате: одно заведение показывается один раз, даже если его опубликовали несколько участников. */
    fun listPublished(chatId: Long, sort: PlaceSort, page: Int, pageSize: Int): PlaceListPage
}
