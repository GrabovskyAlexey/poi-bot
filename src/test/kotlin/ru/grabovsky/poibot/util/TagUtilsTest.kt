package ru.grabovsky.poibot.util

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class TagUtilsTest : ShouldSpec({
    should("split by commas, spaces and new lines, drop hashes, lowercase and deduplicate") {
        TagUtils.parse("#Бар, кафе\nБАР;  Пицца") shouldBe listOf("бар", "кафе", "пицца")
    }

    should("reject empty input, too many tags and too long tags") {
        TagUtils.parse("  , ; ") shouldBe null
        TagUtils.parse("a b c d e f") shouldBe null
        TagUtils.parse("x".repeat(TagUtils.MAX_TAG_LENGTH + 1)) shouldBe null
    }

    should("accept the limits") {
        TagUtils.parse("a b c d e")?.size shouldBe 5
        TagUtils.parse("x".repeat(TagUtils.MAX_TAG_LENGTH))?.size shouldBe 1
    }
})
