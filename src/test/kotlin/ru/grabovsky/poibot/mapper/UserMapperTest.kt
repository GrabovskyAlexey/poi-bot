package ru.grabovsky.poibot.mapper

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.telegram.telegrambots.meta.api.objects.User as TgUser

class UserMapperTest : ShouldSpec({
    should("map telegram user to entity User") {
        val tgUser = mockk<TgUser>()
        every { tgUser.id } returns 321L
        every { tgUser.firstName } returns "John"
        every { tgUser.lastName } returns "Doe"
        every { tgUser.userName } returns "jdoe"
        every { tgUser.languageCode } returns "ru"

        val entity = UserMapper.fromTelegramToEntity(tgUser)

        entity.userId shouldBe 321L
        entity.firstName shouldBe "John"
        entity.lastName shouldBe "Doe"
        entity.userName shouldBe "jdoe"
        entity.language shouldBe "ru"
        entity.profile shouldBe null
    }
})
