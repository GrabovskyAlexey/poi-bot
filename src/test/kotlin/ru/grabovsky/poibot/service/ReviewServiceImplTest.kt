package ru.grabovsky.poibot.service

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import ru.grabovsky.poibot.entity.PlaceComment
import ru.grabovsky.poibot.entity.PlaceCommentReport
import ru.grabovsky.poibot.entity.PlaceCommentReportId
import ru.grabovsky.poibot.entity.PlaceRating
import ru.grabovsky.poibot.entity.PlaceRatingId
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.repository.PlaceCommentReportRepository
import ru.grabovsky.poibot.repository.PlaceCommentRepository
import ru.grabovsky.poibot.repository.PlaceRatingRepository
import ru.grabovsky.poibot.repository.RatingAggregate
import ru.grabovsky.poibot.repository.SavedPlaceRepository
import ru.grabovsky.poibot.service.interfaces.*
import java.util.Optional

class ReviewServiceImplTest : ShouldSpec({
    val ratings = mockk<PlaceRatingRepository>()
    val comments = mockk<PlaceCommentRepository>()
    val reports = mockk<PlaceCommentReportRepository>()
    val savedPlaces = mockk<SavedPlaceRepository>()
    val service = ReviewServiceImpl(ratings, comments, reports, savedPlaces)

    beforeTest {
        clearMocks(ratings, comments, reports, savedPlaces)
        every { savedPlaces.findByIdAndOwnerId(5L, 1L) } returns SavedPlace(id = 5L, ownerId = 1L, placeId = 77L, name = "X")
        every { savedPlaces.findByIdAndOwnerId(5L, 2L) } returns null
    }

    fun aggregate(avg: Double?, count: Long) = object : RatingAggregate {
        override val average = avg
        override val total = count
    }

    context("rating") {
        should("summarize ratings and treat no ratings as empty") {
            every { ratings.aggregate(77L) } returns aggregate(4.25, 4)
            every { ratings.aggregate(78L) } returns aggregate(null, 0)

            service.summary(77L) shouldBe RatingSummary(4.25, 4)
            service.summary(78L) shouldBe RatingSummary.EMPTY
        }

        should("create a rating for the place of an own record") {
            every { ratings.findById(PlaceRatingId(77L, 1L)) } returns Optional.empty()
            val saved = slot<PlaceRating>()
            every { ratings.save(capture(saved)) } answers { saved.captured }

            service.rate(1L, 5L, 4) shouldBe RateResult.OK

            saved.captured.id shouldBe PlaceRatingId(77L, 1L)
            saved.captured.value shouldBe 4
        }

        should("change an existing rating instead of adding another") {
            val existing = PlaceRating(PlaceRatingId(77L, 1L), 2)
            every { ratings.findById(PlaceRatingId(77L, 1L)) } returns Optional.of(existing)
            every { ratings.save(existing) } returns existing

            service.rate(1L, 5L, 5) shouldBe RateResult.OK

            existing.value shouldBe 5
        }

        should("refuse to rate through a foreign record") {
            service.rate(2L, 5L, 5) shouldBe RateResult.NOT_OWNER
            verify(exactly = 0) { ratings.save(any<PlaceRating>()) }
        }

        should("reject values outside 1..5") {
            io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> { service.rate(1L, 5L, 6) }
        }

        should("remove the rating only when it exists") {
            every { ratings.existsById(PlaceRatingId(77L, 1L)) } returns true
            every { ratings.deleteById(PlaceRatingId(77L, 1L)) } returns Unit

            service.removeRating(1L, 5L) shouldBe true

            every { ratings.existsById(PlaceRatingId(77L, 1L)) } returns false
            service.removeRating(1L, 5L) shouldBe false
        }
    }

    context("comments") {
        should("add a trimmed comment to the place") {
            every { comments.findByPlaceIdAndAuthorId(77L, 1L) } returns null
            val saved = slot<PlaceComment>()
            every { comments.save(capture(saved)) } answers { saved.captured }

            service.saveComment(1L, 5L, "  Отличный бар  ") shouldBe AddCommentResult.ADDED

            saved.captured.placeId shouldBe 77L
            saved.captured.authorId shouldBe 1L
            saved.captured.text shouldBe "Отличный бар"
        }

        should("validate length and ownership") {
            service.saveComment(1L, 5L, "x") shouldBe AddCommentResult.TOO_SHORT
            service.saveComment(1L, 5L, "x".repeat(501)) shouldBe AddCommentResult.TOO_LONG
            service.saveComment(2L, 5L, "нормально") shouldBe AddCommentResult.NOT_OWNER
        }

        should("replace the text of the existing comment instead of adding a second one") {
            val existing = PlaceComment(id = 8L, placeId = 77L, authorId = 1L, text = "старый")
            every { comments.findByPlaceIdAndAuthorId(77L, 1L) } returns existing
            every { comments.save(existing) } returns existing

            service.saveComment(1L, 5L, "  новый текст ") shouldBe AddCommentResult.UPDATED

            existing.text shouldBe "новый текст"
            verify(exactly = 1) { comments.save(any<PlaceComment>()) }
        }

        should("not let the author edit a comment hidden by reports") {
            every { comments.findByPlaceIdAndAuthorId(77L, 1L) } returns
                    PlaceComment(id = 8L, placeId = 77L, authorId = 1L, text = "спам", hidden = true)

            service.saveComment(1L, 5L, "исправил") shouldBe AddCommentResult.HIDDEN
            service.userComment(77L, 1L) shouldBe null
        }

        should("return the own visible comment for editing") {
            every { comments.findByPlaceIdAndAuthorId(77L, 1L) } returns
                    PlaceComment(id = 8L, placeId = 77L, authorId = 1L, text = "мой")

            service.userComment(77L, 1L)!!.text shouldBe "мой"
        }

        should("mark own comments and hide the author of others") {
            val items = listOf(
                PlaceComment(id = 1L, placeId = 77L, authorId = 1L, text = "мой"),
                PlaceComment(id = 2L, placeId = 77L, authorId = 9L, text = "чужой"),
            )
            every { comments.findByPlaceIdAndHiddenFalseOrderByCreatedAtDescIdDesc(77L, any()) } returns PageImpl(items)

            val page = service.comments(77L, viewerId = 1L, page = 0, pageSize = 5)

            page.items.map { it.mine } shouldBe listOf(true, false)
            page.total shouldBe 2
        }

        should("let only the author delete a comment") {
            val comment = PlaceComment(id = 3L, placeId = 77L, authorId = 1L, text = "x")
            every { comments.findById(3L) } returns Optional.of(comment)
            every { comments.delete(comment) } returns Unit

            service.deleteComment(2L, 3L) shouldBe false
            service.deleteComment(1L, 3L) shouldBe true
        }
    }

    context("reports") {
        val comment = PlaceComment(id = 3L, placeId = 77L, authorId = 9L, text = "спам")

        beforeTest { every { comments.findById(3L) } returns Optional.of(comment); comment.hidden = false }

        should("store a report and keep the comment below the threshold") {
            every { reports.existsById(PlaceCommentReportId(3L, 1L)) } returns false
            every { reports.saveAndFlush(any<PlaceCommentReport>()) } answers { firstArg() }
            every { reports.countByCommentId(3L) } returns 2

            service.report(1L, 3L) shouldBe ReportResult.REPORTED

            comment.hidden shouldBe false
        }

        should("hide the comment when the threshold is reached") {
            every { reports.existsById(PlaceCommentReportId(3L, 1L)) } returns false
            every { reports.saveAndFlush(any<PlaceCommentReport>()) } answers { firstArg() }
            every { reports.countByCommentId(3L) } returns ReviewService.REPORTS_TO_HIDE.toLong()
            every { comments.save(comment) } returns comment

            service.report(1L, 3L) shouldBe ReportResult.REPORTED

            comment.hidden shouldBe true
        }

        should("refuse duplicate reports, reports on own comments and unknown comments") {
            every { reports.existsById(PlaceCommentReportId(3L, 1L)) } returns true
            every { comments.findById(404L) } returns Optional.empty()

            service.report(1L, 3L) shouldBe ReportResult.ALREADY_REPORTED
            service.report(9L, 3L) shouldBe ReportResult.OWN_COMMENT
            service.report(1L, 404L) shouldBe ReportResult.NOT_FOUND
        }
    }
})
