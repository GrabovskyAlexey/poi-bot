package ru.grabovsky.poibot.geo

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import ru.grabovsky.poibot.entity.SavedPlace

private fun point(distance: Int) =
    NearbyPoint(SavedPlace(id = distance.toLong(), ownerId = 1, placeId = 1, name = "p$distance"), distance)

class GeoUtilsTest : ShouldSpec({
    should("compute known distance between Moscow points") {
        // Красная площадь -> Большой театр, около 700 м
        val distance = GeoUtils.distanceMeters(55.7539, 37.6208, 55.7603, 37.6186)
        distance.shouldBeBetween(650.0, 780.0, 0.0)
    }

    should("return zero for identical points") {
        GeoUtils.distanceMeters(10.0, 20.0, 10.0, 20.0) shouldBe 0.0
    }

    should("build bounding box that contains the whole circle") {
        val lat = 55.75
        val lon = 37.62
        val box = GeoUtils.boundingBox(lat, lon, 1000.0)
        GeoUtils.distanceMeters(lat, lon, lat, box.maxLon).shouldBeGreaterThan(1000.0)
        GeoUtils.distanceMeters(lat, lon, box.maxLat, lon).shouldBeGreaterThan(1000.0)
        GeoUtils.distanceMeters(lat, lon, box.maxLat, lon).shouldBeLessThan(1020.0)
    }
})

class NearbyResultTest : ShouldSpec({
    // 1 точка до 250 м, ещё 3 в (250, 500], ещё 2 в (500, 1000]
    val all = listOf(80, 300, 350, 480, 600, 900).map(::point)

    should("show points of selected radius and hints only for larger radii that add points") {
        val result = NearbyResult.build(all, SearchRadius.M250)

        result.points shouldHaveSize 1
        result.hints shouldBe listOf(
            RadiusHint(SearchRadius.M500, extra = 3, total = 4),
            RadiusHint(SearchRadius.M1000, extra = 2, total = 6),
        )
    }

    should("skip radius without growth in hints") {
        val sparse = listOf(80, 600, 900).map(::point)

        val result = NearbyResult.build(sparse, SearchRadius.M250)

        result.points shouldHaveSize 1
        result.hints shouldBe listOf(RadiusHint(SearchRadius.M1000, extra = 2, total = 3))
    }

    should("not repeat radius without growth when 100 m is selected") {
        val result = NearbyResult.build(all, SearchRadius.M100)

        result.points shouldHaveSize 1
        result.hints.map { it.radius } shouldBe listOf(SearchRadius.M500, SearchRadius.M1000)
    }

    should("return no points and no hints when nothing is nearby") {
        val result = NearbyResult.build(emptyList(), SearchRadius.M250)

        result.points.shouldBeEmpty()
        result.hints.shouldBeEmpty()
    }

    should("describe 100 m selection with first hint relative to zero") {
        val far = listOf(300, 400).map(::point)

        val result = NearbyResult.build(far, SearchRadius.M100)

        result.points.shouldBeEmpty()
        result.hints shouldBe listOf(RadiusHint(SearchRadius.M500, extra = 2, total = 2))
    }
})

class NameMatcherTest : ShouldSpec({
    should("treat names equal after normalization") {
        NameMatcher.similarity("Бар «Хмель»", "хмель") shouldBe 1.0
    }

    should("be tolerant to small typos") {
        NameMatcher.similarity("Хмель", "Хмел").shouldBeGreaterThan(0.7)
    }

    should("score different venues low") {
        NameMatcher.similarity("Хмель", "Пушкин").shouldBeLessThan(0.4)
    }

    should("score containment high") {
        NameMatcher.similarity("Coffee Room Central", "Central").shouldBeGreaterThan(0.7)
    }

    should("return zero for blank names") {
        NameMatcher.similarity("", "Хмель") shouldBe 0.0
    }
})
