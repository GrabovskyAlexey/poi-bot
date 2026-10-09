package ru.grabovsky.poibot.framework

import io.kotest.core.spec.style.ShouldSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import java.util.Properties

class StartupInfoTest : ShouldSpec({
    should("read the version from build info when it is available") {
        val provider = mockk<ObjectProvider<BuildProperties>> {
            every { ifAvailable } returns BuildProperties(Properties().apply { setProperty("version", "0.1.7") })
        }

        StartupInfo(provider).logVersion()

        verify { provider.ifAvailable }
    }

    should("not fail when build info is missing") {
        val provider = mockk<ObjectProvider<BuildProperties>> { every { ifAvailable } returns null }

        StartupInfo(provider).logVersion()

        verify { provider.ifAvailable }
    }
})
