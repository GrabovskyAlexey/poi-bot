package ru.grabovsky.poibot.service

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import ru.grabovsky.poibot.entity.PlaceShareToken
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.repository.PlaceShareTokenRepository
import ru.grabovsky.poibot.repository.SavedPlaceRepository
import ru.grabovsky.poibot.service.interfaces.AcceptResult
import ru.grabovsky.poibot.service.interfaces.PlaceLimitExceededException
import ru.grabovsky.poibot.service.interfaces.SavedPlaceDraft
import ru.grabovsky.poibot.service.interfaces.SavedPlaceService
import java.util.Optional

class SharingServiceImplTest : ShouldSpec({
    val tokens = mockk<PlaceShareTokenRepository>()
    val places = mockk<SavedPlaceRepository>()
    val savedPlaceService = mockk<SavedPlaceService>()
    val service = SharingServiceImpl(tokens, places, savedPlaceService)

    val validToken = "abcdefghijklmnopqrstuv"
    val source = SavedPlace(
        id = 5L, ownerId = 1L, placeId = 77L, name = "Хмель", address = "Ленина 5", description = "Крафт",
        websiteUrl = "https://khmel.ru", photoFileId = "photo", photoFileUniqueId = "u", lat = 55.0, lon = 37.0,
    )

    beforeTest { clearMocks(tokens, places, savedPlaceService) }

    fun knownToken() {
        every { tokens.findById(validToken) } returns Optional.of(PlaceShareToken(validToken, 5L, 1L))
        every { places.findById(5L) } returns Optional.of(source)
    }

    context("getOrCreateToken") {
        should("create a url-safe token for an own place") {
            every { places.findByIdAndOwnerId(5L, 1L) } returns source
            every { tokens.findBySavedPlaceId(5L) } returns null
            val saved = slot<PlaceShareToken>()
            every { tokens.save(capture(saved)) } answers { saved.captured }

            val token = service.getOrCreateToken(1L, 5L)

            token.shouldNotBeNullAnd { it shouldMatch Regex("^[A-Za-z0-9_-]{22}$") }
            saved.captured.savedPlaceId shouldBe 5L
            saved.captured.createdBy shouldBe 1L
        }

        should("reuse the existing token") {
            every { places.findByIdAndOwnerId(5L, 1L) } returns source
            every { tokens.findBySavedPlaceId(5L) } returns PlaceShareToken("existing", 5L, 1L)

            service.getOrCreateToken(1L, 5L) shouldBe "existing"

            verify(exactly = 0) { tokens.save(any<PlaceShareToken>()) }
        }

        should("not create links for foreign places") {
            every { places.findByIdAndOwnerId(5L, 2L) } returns null

            service.getOrCreateToken(2L, 5L) shouldBe null
        }
    }

    context("findShared") {
        should("return the record for a known token") {
            knownToken()

            service.findShared(validToken) shouldBe source
        }

        should("reject malformed tokens without touching the database") {
            service.findShared("short") shouldBe null
            service.findShared("bad token with spaces!!") shouldBe null

            verify(exactly = 0) { tokens.findById(any<String>()) }
        }

        should("return null for an unknown token") {
            every { tokens.findById(validToken) } returns Optional.empty()

            service.findShared(validToken) shouldBe null
        }
    }

    context("accept") {
        should("copy the record to the recipient keeping the same place") {
            knownToken()
            every { places.existsByOwnerIdAndPlaceId(2L, 77L) } returns false
            val draft = slot<SavedPlaceDraft>()
            every { savedPlaceService.create(2L, capture(draft), 77L) } returns
                    SavedPlace(id = 9L, ownerId = 2L, placeId = 77L, name = "Хмель")

            val result = service.accept(2L, validToken)

            result.shouldBeInstanceOf<AcceptResult.Saved>()
            draft.captured.name shouldBe "Хмель"
            draft.captured.photoFileId shouldBe "photo"
            draft.captured.lat shouldBe 55.0
            draft.captured.description shouldBe "Крафт"
        }

        should("not copy a place to its owner") {
            knownToken()

            service.accept(1L, validToken) shouldBe AcceptResult.OwnPlace
        }

        should("not duplicate a place the recipient already has") {
            knownToken()
            every { places.existsByOwnerIdAndPlaceId(2L, 77L) } returns true

            service.accept(2L, validToken) shouldBe AcceptResult.AlreadySaved
            verify(exactly = 0) { savedPlaceService.create(any(), any(), any()) }
        }

        should("report an invalid link") {
            every { tokens.findById(validToken) } returns Optional.empty()

            service.accept(2L, validToken) shouldBe AcceptResult.NotFound
        }

        should("report the limit") {
            knownToken()
            every { places.existsByOwnerIdAndPlaceId(2L, 77L) } returns false
            every { savedPlaceService.create(2L, any(), 77L) } throws PlaceLimitExceededException(500)

            service.accept(2L, validToken) shouldBe AcceptResult.LimitReached
        }
    }
})

private inline fun String?.shouldNotBeNullAnd(check: (String) -> Unit) {
    (this != null) shouldBe true
    check(this!!)
}
