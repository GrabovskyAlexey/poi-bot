package ru.grabovsky.poibot.service

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.repository.SavedPlaceRepository
import ru.grabovsky.poibot.service.interfaces.*

class PlaceListingTest : ShouldSpec({

    fun place(id: Long, name: String, placeId: Long = id, address: String? = null, photo: String? = null, located: Boolean = true) =
        SavedPlace(
            id = id, ownerId = 1L, placeId = placeId, name = name, address = address, photoFileId = photo,
            lat = if (located) 55.0 else null, lon = if (located) 37.0 else null,
        )

    context("matching") {
        should("search by name and address ignoring case, including Cyrillic") {
            val bar = place(1, "Бар Хмель", address = "Ленина 5")

            PlaceListing.matches(bar, PlaceListQuery(text = "ХМЕЛЬ")) shouldBe true
            PlaceListing.matches(bar, PlaceListQuery(text = "ленина")) shouldBe true
            PlaceListing.matches(bar, PlaceListQuery(text = "кофе")) shouldBe false
        }

        should("treat blank text as no search") {
            PlaceListing.matches(place(1, "A"), PlaceListQuery(text = "   ")) shouldBe true
            PlaceListQuery(text = "   ").filtered shouldBe false
        }

        should("apply the location and photo filters") {
            val full = place(1, "A", photo = "p")
            val bare = place(2, "B", located = false)

            PlaceListing.matches(bare, PlaceListQuery(withLocation = true)) shouldBe false
            PlaceListing.matches(full, PlaceListQuery(withLocation = true, withPhoto = true)) shouldBe true
            PlaceListing.matches(place(3, "C"), PlaceListQuery(withPhoto = true)) shouldBe false
        }
    }

    context("sorting") {
        val a = place(1, "Яр"); val b = place(2, "Арбат"); val c = place(3, "Бар")
        val ratings = mapOf(1L to RatingSummary(4.0, 1), 2L to RatingSummary(4.0, 5), 3L to RatingSummary(2.0, 9))

        should("keep the incoming order for newest first") {
            PlaceListing.sort(listOf(a, b, c), PlaceSort.NEW, ratings).map { it.id } shouldBe listOf(1L, 2L, 3L)
        }

        should("sort by name case-insensitively") {
            PlaceListing.sort(listOf(a, b, c), PlaceSort.NAME, ratings).map { it.id } shouldBe listOf(2L, 3L, 1L)
        }

        should("sort by average then by count and put unrated places last") {
            val unrated = place(4, "Без оценок")

            PlaceListing.sort(listOf(unrated, a, b, c), PlaceSort.RATING, ratings).map { it.id } shouldBe listOf(2L, 1L, 3L, 4L)
        }
    }

    should("show one record per physical place keeping the first") {
        val list = listOf(place(1, "X", placeId = 10), place(2, "X copy", placeId = 10), place(3, "Y", placeId = 11))

        PlaceListing.distinctByPlace(list).map { it.id } shouldBe listOf(1L, 3L)
    }

    context("PlaceListServiceImpl") {
        val repository = mockk<SavedPlaceRepository>()
        val reviews = mockk<ReviewService>()
        val service = PlaceListServiceImpl(repository, reviews)

        should("filter, count and page own places and report the unfiltered total") {
            val all = (1L..5L).map { place(it, if (it % 2 == 0L) "Бар $it" else "Кафе $it") }
            every { repository.findAllByOwnerIdOrderByCreatedAtDescIdDesc(1L) } returns all
            every { reviews.summaries(any()) } returns mapOf(2L to RatingSummary(5.0, 1))

            val page = service.searchOwn(1L, PlaceListQuery(text = "бар"), 0, 1)

            page.total shouldBe 2
            page.totalUnfiltered shouldBe 5
            page.totalPages shouldBe 2
            page.items.single().place.id shouldBe 2L
            page.items.single().rating shouldBe RatingSummary(5.0, 1)
        }

        should("clamp a page that is out of range") {
            every { repository.findAllByOwnerIdOrderByCreatedAtDescIdDesc(1L) } returns listOf(place(1, "A"))
            every { reviews.summaries(any()) } returns emptyMap()

            service.searchOwn(1L, PlaceListQuery(), 9, 8).page shouldBe 0
        }

        should("list chat places once per physical place ordered by rating") {
            every { repository.findAllPublishedInChat(-5L) } returns listOf(
                place(1, "Слабое", placeId = 1), place(2, "Лучшее", placeId = 2), place(3, "Лучшее дубль", placeId = 2),
            )
            every { reviews.summaries(any()) } returns mapOf(2L to RatingSummary(5.0, 3), 1L to RatingSummary(3.0, 1))

            val page = service.listPublished(-5L, PlaceSort.RATING, 0, 8)

            page.items.map { it.place.id } shouldBe listOf(2L, 1L)
            page.total shouldBe 2
        }
    }
})
