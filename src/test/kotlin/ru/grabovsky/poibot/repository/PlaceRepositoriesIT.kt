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
import ru.grabovsky.poibot.geo.SearchRadius
import ru.grabovsky.poibot.service.NearbySearchServiceImpl
import ru.grabovsky.poibot.service.PlaceMatchingServiceImpl
import ru.grabovsky.poibot.service.SavedPlaceServiceImpl
import ru.grabovsky.poibot.service.interfaces.SavedPlaceDraft
import ru.grabovsky.poibot.service.ChatServiceImpl
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
