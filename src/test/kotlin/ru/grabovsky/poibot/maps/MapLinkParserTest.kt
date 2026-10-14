package ru.grabovsky.poibot.maps

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class MapLinkParserTest : ShouldSpec({
    val google = "https://www.google.com/maps/place/Crosby+Coffee/@55.883444,37.6543889,19z/data=!4m6!3m5!1s0x1:0x2!8m2!3d55.8834986!4d37.6537285!16s%2Fg%2F11c6mbfjyg?entry=tts"
    val yandex = "https://yandex.ru/maps/213/moscow/?ll=37.654037%2C55.883078&mode=poi&poi%5Bpoint%5D=37.653818%2C55.883147&poi%5Buri%5D=ymapsbm1%3A%2F%2Forg%3Foid%3D211075302874&z=20.2"
    val twoGis = "https://2gis.ru/moscow/inside/4504235283017233/firm/70000001066524600/tab/info?m=37.654784,55.883007/19"

    context("providers") {
        should("recognize map hosts and ignore the rest") {
            MapLinkParser.provider("https://maps.app.goo.gl/nL4ffDTrhXKsRTy56") shouldBe MapProvider.GOOGLE
            MapLinkParser.provider(google) shouldBe MapProvider.GOOGLE
            MapLinkParser.provider("https://yandex.ru/maps/-/CXu5vDnv") shouldBe MapProvider.YANDEX
            MapLinkParser.provider("https://go.2gis.com/GVviQ") shouldBe MapProvider.TWOGIS
            MapLinkParser.provider("https://2gis.ru/moscow/firm/1") shouldBe MapProvider.TWOGIS
            MapLinkParser.provider("https://yandex.ru/search/?text=1") shouldBe null
            MapLinkParser.provider("https://evil.com/maps/place/x") shouldBe null
            MapLinkParser.provider("https://google.com.evil.ru/maps/place/x") shouldBe null
        }

        should("detect short links") {
            MapLinkParser.isShort("https://maps.app.goo.gl/x") shouldBe true
            MapLinkParser.isShort("https://yandex.ru/maps/-/CXu5vDnv") shouldBe true
            MapLinkParser.isShort("https://go.2gis.com/GVviQ") shouldBe true
            MapLinkParser.isShort(google) shouldBe false
        }

        should("find a link inside a text") {
            MapLinkParser.findUrl("Смотри: https://go.2gis.com/GVviQ, класс") shouldBe "https://go.2gis.com/GVviQ"
            MapLinkParser.findUrl("go.2gis.com/GVviQ") shouldBe "https://go.2gis.com/GVviQ"
            MapLinkParser.findUrl("просто текст") shouldBe null
        }
    }

    context("google") {
        should("take the name and the place pin rather than the map center") {
            MapLinkParser.parseGoogle(google) shouldBe MapImport("Crosby Coffee", null, 55.8834986, 37.6537285, "google:0x1:0x2")
        }

        should("fall back to the map center and skip coordinates as a name") {
            MapLinkParser.parseGoogle("https://www.google.com/maps/place/55.75,37.61/@55.75,37.61,17z") shouldBe
                    MapImport(null, null, 55.75, 37.61)
            MapLinkParser.parseGoogle("https://www.google.com/maps?q=55.75,37.61") shouldBe MapImport(null, null, 55.75, 37.61)
        }

        should("return null without coordinates") {
            MapLinkParser.parseGoogle("https://www.google.com/maps/place/Nowhere") shouldBe null
        }
    }

    context("yandex") {
        should("take organization point in longitude,latitude order") {
            MapLinkParser.parseYandex(yandex) shouldBe MapImport(null, null, 55.883147, 37.653818, "yandex:211075302874")
        }

        should("use pt for a plain point and null when there is none") {
            MapLinkParser.parseYandex("https://yandex.ru/maps/?pt=37.6,55.7&z=17") shouldBe MapImport(null, null, 55.7, 37.6)
            MapLinkParser.parseYandex("https://yandex.ru/maps/?ll=37.6%2C55.7&z=17") shouldBe null
        }

        should("add name and address from the page when they match its title") {
            val base = MapImport(null, null, 55.88, 37.65)
            val html = """<title>Панини Пицца, пиццерия, пр. Шокальского, 45, Москва — Яндекс Карты</title>""" +
                    """{"shortTitle":"Панини Пицца","fullAddress":"Москва, проезд Шокальского, 45, \"стр. 1\""}"""

            MapLinkParser.enrichYandex(base, html) shouldBe
                    base.copy(name = "Панини Пицца", address = "Москва, проезд Шокальского, 45, \"стр. 1\"")
        }

        should("ignore page data of another place") {
            val base = MapImport(null, null, 55.88, 37.65)
            val html = """<title>Яндекс Карты</title>{"shortTitle":"Чужое","fullAddress":"Где-то"}"""

            MapLinkParser.enrichYandex(base, html) shouldBe base
        }
    }

    context("2gis") {
        should("take the map center from the link") {
            MapLinkParser.parseTwoGis(twoGis) shouldBe MapImport(null, null, 55.883007, 37.654784, "2gis:70000001066524600")
        }

        should("take a geo point and return null without coordinates") {
            MapLinkParser.parseTwoGis("https://2gis.ru/moscow/geo/4504/37.6%2C55.7") shouldBe MapImport(null, null, 55.7, 37.6)
            MapLinkParser.parseTwoGis("https://2gis.ru/moscow/firm/1") shouldBe null
        }
    }
})
