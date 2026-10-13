package ru.grabovsky.poibot.service

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import ru.grabovsky.poibot.entity.Chat
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.geo.NearbyFilter
import ru.grabovsky.poibot.geo.SearchRadius
import ru.grabovsky.poibot.repository.SavedPlaceRepository
import ru.grabovsky.poibot.repository.UserChatRepository

class NearbySearchServiceImplTest : ShouldSpec({
    val places = mockk<SavedPlaceRepository>()
    val userChats = mockk<UserChatRepository>()
    val service = NearbySearchServiceImpl(places, userChats)

    fun place(id: Long, owner: Long, placeId: Long, name: String, lat: Double) =
        SavedPlace(id = id, ownerId = owner, placeId = placeId, name = name, lat = lat, lon = 37.62)

    // 0.001 градуса широты - около 111 м
    val own = place(1, 7, 100, "Моё", 55.7510)
    val groupNear = place(2, 8, 200, "Из группы", 55.7505)
    val groupSame = place(3, 8, 100, "Моё же в группе", 55.7503)
    val groupDup = place(4, 9, 200, "Из группы (дубль)", 55.7506)

    beforeTest {
        clearMocks(places, userChats)
        every { places.findOwnInBox(7L, any(), any(), any(), any()) } returns listOf(own)
        every { userChats.findActiveChatsByUserId(7L) } returns listOf(Chat(id = -10L, type = "group"), Chat(id = -20L, type = "group"))
        every { places.findPublishedInChatsBox(listOf(-10L, -20L), any(), any(), any(), any()) } returns
                listOf(groupNear, groupSame, groupDup)
    }

    should("add group places once, skip the ones the user already has and sort by distance") {
        val result = service.searchOwn(7L, 55.7500, 37.62, SearchRadius.M1000, includeGroups = true)

        result.points.map { it.place.name } shouldBe listOf("Из группы", "Моё")
        result.points.map { it.fromGroup } shouldBe listOf(true, false)
    }

    should("ignore groups unless asked or when the user has no groups") {
        service.searchOwn(7L, 55.7500, 37.62, SearchRadius.M1000).points.map { it.place.name } shouldBe listOf("Моё")
        verify(exactly = 0) { userChats.findActiveChatsByUserId(any()) }

        every { userChats.findActiveChatsByUserId(7L) } returns emptyList()
        service.searchOwn(7L, 55.7500, 37.62, SearchRadius.M1000, includeGroups = true).points.map { it.place.name } shouldBe
                listOf("Моё")
    }

    should("keep only own places matching the filter and drop group places") {
        val beenBar = own.copy(status = "BEEN", tags = listOf("бар"))
        val wantBar = place(5, 7, 105, "Хочу", 55.7508).copy(status = "WANT", tags = listOf("бар"))
        every { places.findOwnInBox(7L, any(), any(), any(), any()) } returns listOf(beenBar, wantBar)

        val byStatus = service.searchOwn(7L, 55.7500, 37.62, SearchRadius.M1000, includeGroups = true, filter = NearbyFilter(status = "BEEN"))
        byStatus.points.map { it.place.name } shouldBe listOf("Моё")

        val byTag = service.searchOwn(7L, 55.7500, 37.62, SearchRadius.M1000, includeGroups = true, filter = NearbyFilter(tag = "бар"))
        byTag.points.map { it.place.name } shouldBe listOf("Хочу", "Моё")

        val both = service.searchOwn(7L, 55.7500, 37.62, SearchRadius.M1000, filter = NearbyFilter("WANT", "кофе"))
        both.points shouldBe emptyList()
        verify(exactly = 0) { userChats.findActiveChatsByUserId(any()) }
    }

    should("find a place only among the user's groups") {
        every { places.findPublishedInChatsById(listOf(-10L, -20L), 2L) } returns groupNear
        service.findInUserGroups(7L, 2L) shouldBe groupNear

        every { userChats.findActiveChatsByUserId(7L) } returns emptyList()
        service.findInUserGroups(7L, 2L) shouldBe null
    }
})
