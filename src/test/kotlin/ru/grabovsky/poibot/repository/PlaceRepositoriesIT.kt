package ru.grabovsky.poibot.repository

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.DockerClientFactory
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.grabovsky.poibot.entity.Chat
import ru.grabovsky.poibot.entity.User
import ru.grabovsky.poibot.entity.PlaceComment
import ru.grabovsky.poibot.entity.PlaceRating
import ru.grabovsky.poibot.entity.PlaceRatingId
import ru.grabovsky.poibot.service.UserDataServiceImpl
import ru.grabovsky.poibot.service.PlaceListServiceImpl
import ru.grabovsky.poibot.service.interfaces.PlaceSort
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldNotBe
import ru.grabovsky.poibot.geo.SearchRadius
import ru.grabovsky.poibot.service.NearbySearchServiceImpl
import ru.grabovsky.poibot.service.PlaceMatchingServiceImpl
import ru.grabovsky.poibot.service.SavedPlaceServiceImpl
import ru.grabovsky.poibot.service.interfaces.SavedPlaceDraft
import ru.grabovsky.poibot.service.ChatServiceImpl
import ru.grabovsky.poibot.service.SharingServiceImpl
import ru.grabovsky.poibot.service.interfaces.AcceptResult
import ru.grabovsky.poibot.service.interfaces.AddCommentResult
import ru.grabovsky.poibot.service.interfaces.RateResult
import ru.grabovsky.poibot.service.interfaces.RatingSummary
import ru.grabovsky.poibot.service.interfaces.RelinkResult
import ru.grabovsky.poibot.service.interfaces.ReportResult
import ru.grabovsky.poibot.service.PlaceLinkServiceImpl
import ru.grabovsky.poibot.service.ReviewServiceImpl
import io.kotest.matchers.types.shouldBeInstanceOf
import ru.grabovsky.poibot.service.PublishServiceImpl
import ru.grabovsky.poibot.service.interfaces.ChatMembershipChecker

/**
 * Интеграционные проверки на реальном Postgres (Liquibase-миграции, запросы, каскады).
 * Пропускаются, если Docker недоступен.
 */
@DataJpaTest(
    properties = [
        "telegram.token=test-token",
        "telegram.name=test-bot",
    ]
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIf("dockerAvailable")
class PlaceRepositoriesIT {

    @Autowired
    lateinit var em: TestEntityManager

    @Autowired
    lateinit var savedPlaceRepository: SavedPlaceRepository

    @Autowired
    lateinit var placeRepository: PlaceRepository

    @Autowired
    lateinit var chatRepository: ChatRepository

    @Autowired
    lateinit var userChatRepository: UserChatRepository

    @Autowired
    lateinit var savedPlaceChatRepository: SavedPlaceChatRepository

    @Autowired
    lateinit var placeShareTokenRepository: PlaceShareTokenRepository

    @Autowired
    lateinit var placeRatingRepository: PlaceRatingRepository

    @Autowired
    lateinit var placeCommentRepository: PlaceCommentRepository

    @Autowired
    lateinit var placeCommentReportRepository: PlaceCommentReportRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var flowStateRepository: FlowStateRepository

    private fun owner(id: Long) = em.persistAndFlush(User(id, "Test", null, "test$id"))

    private fun saved() = SavedPlaceServiceImpl(savedPlaceRepository, placeRepository)

    @Test
    fun shouldCreateRecordWithPlaceAndFindItNearby() {
        owner(1L)
        val service = saved()
        service.create(1L, SavedPlaceDraft(name = "Близко", lat = 55.7500, lon = 37.6200))
        service.create(1L, SavedPlaceDraft(name = "Далеко", lat = 55.7600, lon = 37.6200)) // ~1.1 км
        service.create(1L, SavedPlaceDraft(name = "Без геопозиции"))
        em.flush()
        em.clear()

        val result = NearbySearchServiceImpl(savedPlaceRepository)
            .searchOwn(1L, 55.7501, 37.6201, SearchRadius.M100)

        result.points.map { it.place.name } shouldBe listOf("Близко")
        placeRepository.count() shouldBe 3
    }

    @Test
    fun shouldNotReturnForeignRecordsInNearbySearch() {
        owner(1L)
        owner(2L)
        saved().create(2L, SavedPlaceDraft(name = "Чужое", lat = 55.75, lon = 37.62))
        em.flush()
        em.clear()

        val result = NearbySearchServiceImpl(savedPlaceRepository).searchOwn(1L, 55.75, 37.62, SearchRadius.M100)

        result.points shouldHaveSize 0
    }

    @Test
    fun shouldFindCandidatePlacesWithinRadius() {
        owner(1L)
        owner(2L)
        val service = saved()
        service.create(1L, SavedPlaceDraft(name = "Хмель", lat = 55.7500, lon = 37.6200))
        em.flush()
        em.clear()

        val candidates = PlaceMatchingServiceImpl(placeRepository).findCandidates(55.7501, 37.6201, "Бар Хмель")

        candidates shouldHaveSize 1
        candidates.first().similarity shouldBe 1.0
    }

    @Test
    fun shouldPageRecordsNewestFirst() {
        owner(1L)
        val service = saved()
        repeat(10) { service.create(1L, SavedPlaceDraft(name = "P$it")) }
        em.flush()
        em.clear()

        val page = service.list(1L, 1, 8)

        page.totalItems shouldBe 10
        page.totalPages shouldBe 2
        page.items shouldHaveSize 2
    }

    @Test
    fun shouldCascadeChatIdChangeToUserLinks() {
        val user = owner(1L)
        em.persistAndFlush(Chat(id = -1L, type = "group", title = "Friends"))
        em.entityManager.createNativeQuery(
            "insert into poi_bot.user_chat (user_id, chat_id) values (${user.userId}, -1)"
        ).executeUpdate()
        em.clear()

        chatRepository.changeId(-1L, -1001L)
        em.flush()

        val count = em.entityManager.createNativeQuery(
            "select count(*) from poi_bot.user_chat where chat_id = -1001"
        ).singleResult as Number
        count.toInt() shouldBe 1
    }

    private fun publishService() = PublishServiceImpl(
        ChatServiceImpl(chatRepository, userChatRepository),
        chatRepository,
        userChatRepository,
        savedPlaceRepository,
        savedPlaceChatRepository,
        object : ChatMembershipChecker {
            override fun isMember(chatId: Long, userId: Long): Boolean? = true
            override fun isAdmin(chatId: Long, userId: Long): Boolean = false
        },
    )

    private fun group(id: Long, userId: Long) {
        em.persistAndFlush(Chat(id = id, type = "supergroup", title = "Group $id"))
        em.entityManager.createNativeQuery(
            "insert into poi_bot.user_chat (user_id, chat_id) values ($userId, $id)"
        ).executeUpdate()
    }

    @Test
    fun shouldPublishRecordsAndShowThemOnlyInThatChat() {
        owner(1L)
        group(-10L, 1L)
        group(-20L, 1L)
        val records = saved()
        val first = records.create(1L, SavedPlaceDraft(name = "Опубликовано", lat = 55.7500, lon = 37.6200))
        val second = records.create(1L, SavedPlaceDraft(name = "Личное", lat = 55.7501, lon = 37.6201))
        em.flush()
        em.clear()
        val publish = publishService()

        publish.setPublished(1L, listOf(first.id!!, second.id!!), -10L, true) shouldBe 2
        publish.setPublished(1L, listOf(second.id!!), -10L, false) shouldBe 1
        em.flush()
        em.clear()

        publish.listPublished(-10L, 0, 8).items.map { it.name } shouldBe listOf("Опубликовано")
        publish.listPublished(-20L, 0, 8).items shouldHaveSize 0
        publish.getPublished(-10L, second.id!!) shouldBe null
        publish.publishedCounts(listOf(first.id!!, second.id!!), listOf(-10L, -20L)) shouldBe mapOf(-10L to 1)
        val nearby = NearbySearchServiceImpl(savedPlaceRepository)
        nearby.searchInChat(-10L, 55.7500, 37.6200, SearchRadius.M100).points.map { it.place.name } shouldBe
            listOf("Опубликовано")
        nearby.searchInChat(-20L, 55.7500, 37.6200, SearchRadius.M100).points shouldHaveSize 0
    }

    @Test
    fun shouldRemovePublicationsWhenRecordIsDeletedAndKeepThemOnChatIdChange() {
        owner(1L)
        group(-30L, 1L)
        val record = saved().create(1L, SavedPlaceDraft(name = "Хмель", lat = 55.75, lon = 37.62))
        em.flush()
        em.clear()
        val publish = publishService()
        publish.setPublished(1L, listOf(record.id!!), -30L, true)
        em.flush()

        chatRepository.changeId(-30L, -1030L)
        em.flush()
        em.clear()
        publish.listPublished(-1030L, 0, 8).items shouldHaveSize 1

        saved().delete(1L, record.id!!) shouldBe true
        em.flush()
        em.clear()
        savedPlaceChatRepository.count() shouldBe 0
    }

    @Test
    fun shouldPageAllPublishedRecordsOfChat() {
        owner(1L)
        group(-40L, 1L)
        val service = saved()
        val ids = (1..10).map { service.create(1L, SavedPlaceDraft(name = "P$it")).id!! }
        em.flush()
        em.clear()
        val publish = publishService()
        publish.setPublished(1L, ids, -40L, true)
        em.flush()
        em.clear()

        val secondPage = publish.listPublished(-40L, 1, 8)
        secondPage.totalItems shouldBe 10
        secondPage.totalPages shouldBe 2
        secondPage.items shouldHaveSize 2
        publish.listPublished(-40L, 5, 8).page shouldBe 1
    }

    @Test
    fun shouldShareRecordAndCopyItToAnotherUserKeepingTheSamePlace() {
        owner(1L)
        owner(2L)
        val records = saved()
        val original = records.create(1L, SavedPlaceDraft(name = "Хмель", description = "Крафт", lat = 55.75, lon = 37.62))
        em.flush()
        em.clear()
        val sharing = SharingServiceImpl(placeShareTokenRepository, savedPlaceRepository, records)

        val token = sharing.getOrCreateToken(1L, original.id!!)!!

        sharing.getOrCreateToken(1L, original.id!!) shouldBe token
        sharing.getOrCreateToken(2L, original.id!!) shouldBe null
        val result = sharing.accept(2L, token)
        result.shouldBeInstanceOf<AcceptResult.Saved>()
        em.flush()
        em.clear()

        val copy = (result as AcceptResult.Saved).place
        copy.ownerId shouldBe 2L
        copy.placeId shouldBe original.placeId
        copy.description shouldBe "Крафт"
        sharing.accept(2L, token) shouldBe AcceptResult.AlreadySaved
        sharing.accept(1L, token) shouldBe AcceptResult.OwnPlace
        placeRepository.count() shouldBe 1
    }

    @Test
    fun shouldInvalidateShareLinkWhenRecordIsDeleted() {
        owner(1L)
        val records = saved()
        val original = records.create(1L, SavedPlaceDraft(name = "Удалю"))
        em.flush()
        em.clear()
        val sharing = SharingServiceImpl(placeShareTokenRepository, savedPlaceRepository, records)
        val token = sharing.getOrCreateToken(1L, original.id!!)!!
        em.flush()

        records.delete(1L, original.id!!) shouldBe true
        em.flush()
        em.clear()

        sharing.findShared(token) shouldBe null
        placeShareTokenRepository.count() shouldBe 0
    }

    private fun reviews() = ReviewServiceImpl(
        placeRatingRepository, placeCommentRepository, placeCommentReportRepository, savedPlaceRepository,
    )

    private fun links() = PlaceLinkServiceImpl(
        savedPlaceRepository, placeRepository, placeRatingRepository, placeCommentRepository,
        PlaceMatchingServiceImpl(placeRepository),
    )

    @Test
    fun shouldShareRatingAndCommentsBetweenUsersOfTheSamePlace() {
        owner(1L)
        owner(2L)
        val records = saved()
        val first = records.create(1L, SavedPlaceDraft(name = "Хмель", lat = 55.75, lon = 37.62))
        val second = records.create(2L, SavedPlaceDraft(name = "Бар Хмель"), linkPlaceId = first.placeId)
        em.flush()
        em.clear()
        val service = reviews()

        service.rate(1L, first.id!!, 5) shouldBe RateResult.OK
        service.rate(2L, second.id!!, 4) shouldBe RateResult.OK
        service.rate(2L, second.id!!, 3) shouldBe RateResult.OK
        service.saveComment(1L, first.id!!, "Отличный крафт") shouldBe AddCommentResult.ADDED
        em.flush()
        em.clear()

        service.summary(first.placeId).count shouldBe 2
        service.summary(first.placeId).average shouldBe 4.0
        service.commentCount(second.placeId) shouldBe 1
        service.comments(second.placeId, 2L, 0, 5).items.single().mine shouldBe false
        service.comments(second.placeId, 1L, 0, 5).items.single().mine shouldBe true
    }

    @Test
    fun shouldHideCommentAfterEnoughReportsAndExcludeItFromCounts() {
        (1L..5L).forEach { owner(it) }
        val records = saved()
        val record = records.create(1L, SavedPlaceDraft(name = "Хмель"))
        em.flush()
        em.clear()
        val service = reviews()
        service.saveComment(1L, record.id!!, "Реклама казино") shouldBe AddCommentResult.ADDED
        em.flush()
        em.clear()
        val commentId = service.comments(record.placeId, 1L, 0, 5).items.single().id

        service.report(2L, commentId) shouldBe ReportResult.REPORTED
        service.report(2L, commentId) shouldBe ReportResult.ALREADY_REPORTED
        service.report(3L, commentId) shouldBe ReportResult.REPORTED
        service.commentCount(record.placeId) shouldBe 1
        service.report(4L, commentId) shouldBe ReportResult.REPORTED
        em.flush()
        em.clear()

        service.commentCount(record.placeId) shouldBe 0
        service.comments(record.placeId, 1L, 0, 5).items shouldHaveSize 0
    }

    @Test
    fun shouldMergeDuplicatePlacesWhenLastRecordIsRelinked() {
        owner(1L)
        owner(2L)
        val records = saved()
        val kept = records.create(1L, SavedPlaceDraft(name = "Хмель", lat = 55.7500, lon = 37.6200))
        val duplicate = records.create(2L, SavedPlaceDraft(name = "Хмель бар", lat = 55.7501, lon = 37.6201))
        em.flush()
        em.clear()
        val reviewService = reviews()
        reviewService.rate(1L, kept.id!!, 5)
        reviewService.rate(2L, duplicate.id!!, 3)
        reviewService.saveComment(2L, duplicate.id!!, "Комментарий со второго места")
        em.flush()
        em.clear()
        val linkService = links()

        linkService.candidatesFor(2L, duplicate.id!!).map { it.placeId } shouldBe listOf(kept.placeId)
        linkService.relink(2L, duplicate.id!!, kept.placeId) shouldBe RelinkResult.RELINKED
        linkService.relink(2L, duplicate.id!!, kept.placeId) shouldBe RelinkResult.SAME_PLACE
        em.flush()
        em.clear()

        savedPlaceRepository.findById(duplicate.id!!).get().placeId shouldBe kept.placeId
        placeRepository.findById(duplicate.placeId).get().mergedIntoId shouldBe kept.placeId
        val summary = reviewService.summary(kept.placeId)
        summary.count shouldBe 2
        summary.average shouldBe 4.0
        reviewService.commentCount(kept.placeId) shouldBe 1
        reviewService.summary(duplicate.placeId) shouldBe RatingSummary.EMPTY
        PlaceMatchingServiceImpl(placeRepository).findCandidates(55.7500, 37.6200, "Хмель")
            .map { it.placeId } shouldBe listOf(kept.placeId)
    }

    @Test
    fun shouldKeepOneCommentPerUserAndPlaceAndResolveMergeConflicts() {
        owner(1L)
        owner(2L)
        val records = saved()
        val kept = records.create(1L, SavedPlaceDraft(name = "Хмель", lat = 55.7500, lon = 37.6200))
        val duplicate = records.create(2L, SavedPlaceDraft(name = "Хмель бар", lat = 55.7501, lon = 37.6201))
        val extra = records.create(1L, SavedPlaceDraft(name = "Хмель второй", lat = 55.7502, lon = 37.6202), linkPlaceId = duplicate.placeId)
        em.flush()
        em.clear()
        val service = reviews()

        service.saveComment(1L, kept.id!!, "первый") shouldBe AddCommentResult.ADDED
        service.saveComment(1L, kept.id!!, "правка") shouldBe AddCommentResult.UPDATED
        service.saveComment(1L, extra.id!!, "тот же автор на втором месте") shouldBe AddCommentResult.ADDED
        service.saveComment(2L, duplicate.id!!, "другой автор") shouldBe AddCommentResult.ADDED
        em.flush()
        em.clear()
        service.commentCount(kept.placeId) shouldBe 1
        service.userComment(kept.placeId, 1L)!!.text shouldBe "правка"

        links().relink(2L, duplicate.id!!, kept.placeId) shouldBe RelinkResult.RELINKED
        links().relink(1L, extra.id!!, kept.placeId) shouldBe RelinkResult.RELINKED
        em.flush()
        em.clear()

        service.commentCount(kept.placeId) shouldBe 2
        service.userComment(kept.placeId, 1L)!!.text shouldBe "правка"
    }

    @Test
    fun shouldKeepOldPlaceWhenOtherRecordsStillUseIt() {
        owner(1L)
        owner(2L)
        owner(3L)
        val records = saved()
        val target = records.create(1L, SavedPlaceDraft(name = "Цель", lat = 55.7500, lon = 37.6200))
        val shared = records.create(2L, SavedPlaceDraft(name = "Общее", lat = 55.7501, lon = 37.6201))
        val second = records.create(3L, SavedPlaceDraft(name = "Общее копия"), linkPlaceId = shared.placeId)
        em.flush()
        em.clear()

        links().relink(2L, shared.id!!, target.placeId) shouldBe RelinkResult.RELINKED
        em.flush()
        em.clear()

        placeRepository.findById(shared.placeId).get().mergedIntoId shouldBe null
        savedPlaceRepository.findById(second.id!!).get().placeId shouldBe shared.placeId
    }

    @Test
    fun shouldDeleteAllUserDataAndKeepSharedPlaces() {
        owner(1L)
        owner(2L)
        val records = saved()
        val own = records.create(1L, SavedPlaceDraft(name = "Только моё", lat = 55.70, lon = 37.60))
        val shared = records.create(1L, SavedPlaceDraft(name = "Общее", lat = 55.75, lon = 37.62))
        val foreign = records.create(2L, SavedPlaceDraft(name = "Общее у друга"), linkPlaceId = shared.placeId)
        placeRatingRepository.save(PlaceRating(PlaceRatingId(shared.placeId, 1L), 5))
        placeCommentRepository.save(PlaceComment(placeId = shared.placeId, authorId = 1L, text = "Хорошо"))
        em.flush()
        em.clear()
        val service = UserDataServiceImpl(
            userRepository, savedPlaceRepository, placeRepository, placeRatingRepository,
            placeCommentRepository, flowStateRepository, ObjectMapper(),
        )

        service.export(1L).toString(Charsets.UTF_8) shouldContain "Только моё"
        service.deleteAll(1L)
        em.flush()
        em.clear()

        userRepository.findUserByUserId(1L) shouldBe null
        savedPlaceRepository.countByOwnerId(1L) shouldBe 0
        placeRepository.findById(own.placeId).isPresent shouldBe false
        placeRepository.findById(shared.placeId).isPresent shouldBe true
        savedPlaceRepository.findById(foreign.id!!).isPresent shouldBe true
        placeRatingRepository.findAllByPlaceId(shared.placeId) shouldHaveSize 0
        placeCommentRepository.countByPlaceIdAndHiddenFalse(shared.placeId) shouldBe 0
        userRepository.findUserByUserId(2L) shouldNotBe null
    }

    @Test
    fun shouldAggregateRatingsAndShowSharedPlaceOnceInChat() {
        owner(1L)
        owner(2L)
        group(-41L, 1L)
        em.entityManager.createNativeQuery("insert into poi_bot.user_chat (user_id, chat_id) values (2, -41)").executeUpdate()
        val records = saved()
        val mine = records.create(1L, SavedPlaceDraft(name = "Общее", lat = 55.7500, lon = 37.6200))
        val friend = records.create(2L, SavedPlaceDraft(name = "Общее у друга", lat = 55.7501, lon = 37.6201), linkPlaceId = mine.placeId)
        val other = records.create(1L, SavedPlaceDraft(name = "Другое", lat = 55.7502, lon = 37.6202))
        placeRatingRepository.save(PlaceRating(PlaceRatingId(mine.placeId, 1L), 5))
        placeRatingRepository.save(PlaceRating(PlaceRatingId(mine.placeId, 2L), 3))
        em.flush()
        em.clear()
        val publish = publishService()
        publish.setPublished(1L, listOf(mine.id!!, other.id!!), -41L, true)
        publish.setPublished(2L, listOf(friend.id!!), -41L, true)
        em.flush()
        em.clear()

        val aggregates = placeRatingRepository.aggregates(listOf(mine.placeId, other.placeId)).associateBy { it.placeId }
        aggregates.keys shouldBe setOf(mine.placeId)
        aggregates.getValue(mine.placeId).average shouldBe 4.0
        aggregates.getValue(mine.placeId).total shouldBe 2

        val list = PlaceListServiceImpl(savedPlaceRepository, reviews())
        val page = list.listPublished(-41L, PlaceSort.RATING, 0, 8)
        page.total shouldBe 2
        page.items.first().rating.count shouldBe 2
        NearbySearchServiceImpl(savedPlaceRepository).searchInChat(-41L, 55.7500, 37.6200, SearchRadius.M100)
            .points shouldHaveSize 2
    }

    companion object {
        private val postgres: PostgreSQLContainer by lazy { PostgreSQLContainer("postgres:17") }

        @JvmStatic
        fun dockerAvailable(): Boolean = DockerClientFactory.instance().isDockerAvailable

        @JvmStatic
        @DynamicPropertySource
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            postgres.start()
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }
}
