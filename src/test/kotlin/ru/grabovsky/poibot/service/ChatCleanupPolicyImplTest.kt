package ru.grabovsky.poibot.service

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import ru.grabovsky.poibot.entity.User
import ru.grabovsky.poibot.entity.UserProfile
import ru.grabovsky.poibot.entity.UserSettings
import ru.grabovsky.poibot.service.interfaces.UserService

class ChatCleanupPolicyImplTest : ShouldSpec({
    val userService = mockk<UserService>()
    val policy = ChatCleanupPolicyImpl(userService)

    should("clean by default, for unknown users and for groups") {
        every { userService.getUser(1L) } returns User(1L, "T", null, "t").apply { profile = UserProfile() }
        every { userService.getUser(2L) } returns null
        every { userService.getUser(-100L) } returns null

        policy.enabled(1L) shouldBe true
        policy.enabled(2L) shouldBe true
        policy.enabled(-100L) shouldBe true
    }

    should("respect the user choice") {
        every { userService.getUser(1L) } returns
                User(1L, "T", null, "t").apply { profile = UserProfile(settings = UserSettings(cleanChat = false)) }

        policy.enabled(1L) shouldBe false
    }
})
