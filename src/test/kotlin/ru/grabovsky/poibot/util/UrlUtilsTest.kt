package ru.grabovsky.poibot.util

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class UrlUtilsTest : ShouldSpec({
    should("add https scheme when missing") {
        UrlUtils.normalize("khmel.ru/menu") shouldBe "https://khmel.ru/menu"
    }

    should("keep explicit http scheme") {
        UrlUtils.normalize("http://khmel.ru") shouldBe "http://khmel.ru"
    }

    should("reject text, spaces and non http schemes") {
        UrlUtils.normalize("просто текст") shouldBe null
        UrlUtils.normalize("khmel") shouldBe null
        UrlUtils.normalize("ftp://khmel.ru") shouldBe null
        UrlUtils.normalize("https://localhost") shouldBe null
    }

    should("detect link-like messages") {
        UrlUtils.looksLikeUrl("khmel.ru") shouldBe true
        UrlUtils.looksLikeUrl("https://x.y/z") shouldBe true
        UrlUtils.looksLikeUrl("Хмель") shouldBe false
        UrlUtils.looksLikeUrl("Привет. Как дела") shouldBe false
        UrlUtils.looksLikeUrl("конец.") shouldBe false
    }
})
