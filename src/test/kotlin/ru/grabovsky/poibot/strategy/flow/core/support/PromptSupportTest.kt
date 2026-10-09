package ru.grabovsky.poibot.strategy.flow.core.support

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import ru.grabovsky.poibot.strategy.flow.core.engine.DeleteMessageAction
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKey

private data class TestPromptState(
    override val promptBindings: MutableList<String> = mutableListOf()
) : PromptState

class PromptSupportTest : ShouldSpec({
    should("build cancel button with default coordinates and payload") {
        val key = FlowKey("FLOW")
        val button = key.cancelPromptButton("Cancel")

        button.text shouldBe "Cancel"
        button.payload.flow shouldBe key.value
        button.payload.data shouldBe "PROMPT:CANCEL"
        button.row shouldBe 0
        button.col shouldBe 0
    }

    should("cleanup prompt bindings and return delete actions") {
        val state = TestPromptState(mutableListOf("first", "second"))

        val actions = state.cleanupPromptMessages()

        actions.shouldHaveSize(2)
        actions.shouldContainExactly(
            DeleteMessageAction("first"),
            DeleteMessageAction("second")
        )
        state.promptBindings shouldBe mutableListOf<String>()
    }
})
