package ru.grabovsky.poibot.service

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import ru.grabovsky.poibot.maps.MapImport
import ru.grabovsky.poibot.service.interfaces.MapLinkHttp

class MapLinkImportServiceImplTest : ShouldSpec({
    val http = mockk<MapLinkHttp>()
    val service = MapLinkImportServiceImpl(http)
    val googleLong = "https://www.google.com/maps/place/Crosby+Coffee/@55.88,37.65,19z/data=!3d55.8834986!4d37.6537285"
    val yandexLong = "https://yandex.ru/maps/213/moscow/?mode=poi&poi%5Bpoint%5D=37.653818%2C55.883147&poi%5Buri%5D=ymapsbm1%3A%2F%2Forg%3Foid%3D1"

    beforeTest {
        clearMocks(http)
        every { http.reverseAddress(any(), any()) } returns null
    }

    should("follow a short Google link and add the address by coordinates") {
        every { http.redirectTarget("https://maps.app.goo.gl/x") } returns googleLong
        every { http.reverseAddress(55.8834986, 37.6537285) } returns "проезд Шокальского, 45 к3, Москва"

        service.import("https://maps.app.goo.gl/x") shouldBe
                MapImport("Crosby Coffee", "проезд Шокальского, 45 к3, Москва", 55.8834986, 37.6537285)
    }

    should("take name and address of a Yandex organization from its page without geocoding") {
        every { http.redirectTarget("https://yandex.ru/maps/-/CXu5vDnv") } returns yandexLong
        every { http.page(yandexLong) } returns
                """<title>Панини Пицца, пиццерия — Яндекс Карты</title>{"shortTitle":"Панини Пицца","fullAddress":"Москва, Шокальского, 45"}"""

        service.import("Вот: https://yandex.ru/maps/-/CXu5vDnv") shouldBe
                MapImport("Панини Пицца", "Москва, Шокальского, 45", 55.883147, 37.653818, "yandex:1")
        verify(exactly = 0) { http.reverseAddress(any(), any()) }
    }

    should("keep Yandex coordinates when the page is unavailable") {
        every { http.page(yandexLong) } returns null

        service.import(yandexLong) shouldBe MapImport(null, null, 55.883147, 37.653818, "yandex:1")
    }

    should("give only coordinates (and geocoded address) for 2GIS") {
        every { http.redirectTarget("https://go.2gis.com/GVviQ") } returns
                "https://2gis.ru/moscow/firm/7/tab/info?m=37.654784,55.883007/19"

        service.import("https://go.2gis.com/GVviQ") shouldBe MapImport(null, null, 55.883007, 37.654784, "2gis:7")
    }

    should("ignore foreign links and never leave map hosts through redirects") {
        service.import("https://example.com/maps/place/x") shouldBe null
        every { http.redirectTarget("https://maps.app.goo.gl/x") } returns "http://169.254.169.254/latest"

        service.import("https://maps.app.goo.gl/x") shouldBe null
    }

    should("return null when a short link does not redirect") {
        every { http.redirectTarget("https://go.2gis.com/dead") } returns null

        service.import("https://go.2gis.com/dead") shouldBe null
    }
})
