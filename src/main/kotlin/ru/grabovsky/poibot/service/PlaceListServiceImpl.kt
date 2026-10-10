package ru.grabovsky.poibot.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.repository.SavedPlaceRepository
import ru.grabovsky.poibot.service.interfaces.*

@Service
class PlaceListServiceImpl(
    private val savedPlaceRepository: SavedPlaceRepository,
    private val reviewService: ReviewService,
) : PlaceListService {

    @Transactional(readOnly = true)
    override fun searchOwn(ownerId: Long, query: PlaceListQuery, page: Int, pageSize: Int): PlaceListPage {
        val all = savedPlaceRepository.findAllByOwnerIdOrderByCreatedAtDescIdDesc(ownerId)
        val matched = all.filter { PlaceListing.matches(it, query) }
        return paged(matched, query.sort, page, pageSize, all.size.toLong())
    }

    @Transactional(readOnly = true)
    override fun listPublished(chatId: Long, sort: PlaceSort, page: Int, pageSize: Int): PlaceListPage {
        val distinct = PlaceListing.distinctByPlace(savedPlaceRepository.findAllPublishedInChat(chatId))
        return paged(distinct, sort, page, pageSize, distinct.size.toLong())
    }

    private fun paged(places: List<SavedPlace>, sort: PlaceSort, page: Int, pageSize: Int, totalUnfiltered: Long): PlaceListPage {
        // Оценки нужны для сортировки по рейтингу (по всем местам) или только для показанной страницы
        val ratings = if (sort == PlaceSort.RATING) reviewService.summaries(places.map { it.placeId }) else emptyMap()
        val sorted = PlaceListing.sort(places, sort, ratings)
        val totalPages = ((sorted.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val safePage = page.coerceIn(0, totalPages - 1)
        val items = sorted.drop(safePage * pageSize).take(pageSize)
        val pageRatings = if (ratings.isNotEmpty()) ratings else reviewService.summaries(items.map { it.placeId })
        return PlaceListPage(PlaceListing.entries(items, pageRatings), safePage, totalPages, sorted.size.toLong(), totalUnfiltered)
    }
}
