package ru.grabovsky.poibot.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import ru.grabovsky.poibot.entity.Place
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.repository.PlaceRepository
import ru.grabovsky.poibot.repository.SavedPlaceRepository
import ru.grabovsky.poibot.service.interfaces.PlaceLimitExceededException
import ru.grabovsky.poibot.service.interfaces.SavedPlaceDraft
import ru.grabovsky.poibot.service.interfaces.SavedPlaceService
import java.util.Optional

class SavedPlaceServiceImplTest : ShouldSpec({
    val savedRepo = mockk<SavedPlaceRepository>()
    val placeRepo = mockk<PlaceRepository>()
    val service = SavedPlaceServiceImpl(savedRepo, placeRepo)

    beforeTest { clearMocks(savedRepo, placeRepo) }

    should("create new Place together with the record when no link is given") {
        every { savedRepo.countByOwnerId(1L) } returns 0
        every { placeRepo.save(any<Place>()) } answers { firstArg<Place>().copy(id = 10L) }
        val saved = slot<SavedPlace>()
        every { savedRepo.save(capture(saved)) } answers { saved.captured }

        service.create(1L, SavedPlaceDraft(name = "Хмель", lat = 55.0, lon = 37.0))

        saved.captured.placeId shouldBe 10L
        saved.captured.name shouldBe "Хмель"
        saved.captured.lat shouldBe 55.0
    }

    should("link to existing Place and follow merge chain") {
        every { savedRepo.countByOwnerId(1L) } returns 0
        every { placeRepo.findById(5L) } returns Optional.of(Place(id = 5L, displayName = "old", mergedIntoId = 6L))
        every { placeRepo.findById(6L) } returns Optional.of(Place(id = 6L, displayName = "actual"))
        val saved = slot<SavedPlace>()
        every { savedRepo.save(capture(saved)) } answers { saved.captured }

        service.create(1L, SavedPlaceDraft(name = "Хмель"), linkPlaceId = 5L)

        saved.captured.placeId shouldBe 6L
        verify(exactly = 0) { placeRepo.save(any<Place>()) }
    }

    should("reject creation above the per-user limit") {
        every { savedRepo.countByOwnerId(1L) } returns SavedPlaceService.MAX_PLACES_PER_USER.toLong()

        shouldThrow<PlaceLimitExceededException> {
            service.create(1L, SavedPlaceDraft(name = "x"))
        }
    }

    should("update own Place in place when geo changes and Place is not shared") {
        val existing = SavedPlace(id = 3L, ownerId = 1L, placeId = 10L, name = "A", lat = 1.0, lon = 1.0)
        val place = Place(id = 10L, displayName = "A", lat = 1.0, lon = 1.0)
        every { savedRepo.findByIdAndOwnerId(3L, 1L) } returns existing
        every { savedRepo.countByPlaceId(10L) } returns 1
        every { placeRepo.findById(10L) } returns Optional.of(place)
        every { placeRepo.save(place) } returns place
        every { savedRepo.save(existing) } returns existing

        service.update(1L, 3L, SavedPlaceDraft(name = "A", lat = 2.0, lon = 2.0))

        place.lat shouldBe 2.0
        existing.placeId shouldBe 10L
    }

    should("create a new Place when geo changes and Place is shared with other records") {
        val existing = SavedPlace(id = 3L, ownerId = 1L, placeId = 10L, name = "A", lat = 1.0, lon = 1.0)
        every { savedRepo.findByIdAndOwnerId(3L, 1L) } returns existing
        every { savedRepo.countByPlaceId(10L) } returns 2
        every { placeRepo.findById(10L) } returns Optional.of(Place(id = 10L, displayName = "A"))
        every { placeRepo.save(any<Place>()) } answers { firstArg<Place>().copy(id = 11L) }
        every { savedRepo.save(existing) } returns existing

        service.update(1L, 3L, SavedPlaceDraft(name = "A", lat = 2.0, lon = 2.0))

        existing.placeId shouldBe 11L
    }

    should("return null when updating a record of another owner") {
        every { savedRepo.findByIdAndOwnerId(3L, 2L) } returns null

        service.update(2L, 3L, SavedPlaceDraft(name = "A")) shouldBe null
    }
})

class PlaceMatchingServiceImplTest : ShouldSpec({
    val placeRepo = mockk<PlaceRepository>()
    val service = PlaceMatchingServiceImpl(placeRepo)

    should("return only places within radius sorted by name similarity") {
        val lat = 55.75
        val lon = 37.62
        val sameName = Place(id = 1L, displayName = "Хмель", lat = lat + 0.0003, lon = lon) // ~33 м
        val otherName = Place(id = 2L, displayName = "Пушкин", lat = lat + 0.0001, lon = lon) // ~11 м
        val tooFar = Place(id = 3L, displayName = "Хмель", lat = lat + 0.002, lon = lon) // ~220 м
        every { placeRepo.findInBox(any(), any(), any(), any()) } returns listOf(otherName, sameName, tooFar)

        val result = service.findCandidates(lat, lon, "Бар Хмель")

        result shouldHaveSize 2
        result[0].placeId shouldBe 1L
        result[0].similarity shouldBe 1.0
        result[1].placeId shouldBe 2L
    }
})
