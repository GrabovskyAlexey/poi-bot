package ru.grabovsky.poibot.service

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import ru.grabovsky.poibot.entity.Place
import ru.grabovsky.poibot.entity.PlaceComment
import ru.grabovsky.poibot.repository.PlaceCommentReportRepository
import ru.grabovsky.poibot.repository.PlaceCommentRepository
import ru.grabovsky.poibot.repository.PlaceRepository
import ru.grabovsky.poibot.service.interfaces.HiddenComment
import java.util.Optional

class ModerationServiceImplTest : ShouldSpec({
    val comments = mockk<PlaceCommentRepository>(relaxed = true)
    val reports = mockk<PlaceCommentReportRepository>(relaxed = true)
    val places = mockk<PlaceRepository>()
    val service = ModerationServiceImpl(comments, reports, places)

    fun comment(hidden: Boolean) = PlaceComment(id = 10, placeId = 5, authorId = 1, text = "Плохо", hidden = hidden)

    beforeTest { clearMocks(comments, reports, places) }

    should("describe the hidden comment with its place and number of reports") {
        every { comments.findByHiddenTrueOrderByIdAsc(PageRequest.of(2, 1)) } returns
                PageImpl(listOf(comment(hidden = true)), PageRequest.of(2, 1), 3)
        every { places.findById(5L) } returns Optional.of(Place(id = 5, displayName = "Хмель", address = "Тверская, 1"))
        every { reports.countByCommentId(10L) } returns 3

        service.hiddenAt(2) shouldBe HiddenComment(10, "Плохо", "Хмель", "Тверская, 1", 3)
        service.hiddenAt(-1) shouldBe null
    }

    should("return null when there is no hidden comment at the position") {
        every { comments.findByHiddenTrueOrderByIdAsc(PageRequest.of(4, 1)) } returns PageImpl(emptyList(), PageRequest.of(4, 1), 3)

        service.hiddenAt(4) shouldBe null
    }

    should("restore a hidden comment and drop its reports") {
        val hidden = comment(hidden = true)
        every { comments.findById(10L) } returns Optional.of(hidden)

        every { comments.save(any<PlaceComment>()) } answers { firstArg() }

        service.restore(10L) shouldBe true

        hidden.hidden shouldBe false
        verify { reports.deleteByCommentId(10L) }
        verify { comments.save(hidden) }
    }

    should("delete only hidden comments") {
        val hidden = comment(hidden = true)
        every { comments.findById(10L) } returns Optional.of(hidden)
        service.delete(10L) shouldBe true
        verify { comments.delete(hidden) }

        every { comments.findById(11L) } returns Optional.of(comment(hidden = false))
        service.delete(11L) shouldBe false
        service.restore(11L) shouldBe false
        every { comments.findById(12L) } returns Optional.empty()
        service.delete(12L) shouldBe false
    }
})
