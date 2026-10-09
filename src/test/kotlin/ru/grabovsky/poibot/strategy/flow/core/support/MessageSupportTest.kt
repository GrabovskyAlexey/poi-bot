package ru.grabovsky.poibot.strategy.flow.core.support

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import ru.grabovsky.poibot.strategy.flow.core.engine.*

private object TestStep : FlowStep {
    override val key: String = "test_step"
}

class MessageSupportTest : ShouldSpec({
    should("build flow message with provided data") {
        val key = FlowKey("TEST")
        val inlineButton = FlowInlineButton(
            text = "btn",
            payload = FlowCallbackPayload(key.value, "DATA"),
            row = 1,
            col = 2
        )
        val replyButton = FlowReplyButton(text = "reply")

        val message = key.buildMessage(
            step = TestStep,
            model = mapOf("foo" to "bar"),
            inlineButtons = listOf(inlineButton),
            replyButtons = listOf(replyButton),
            parseMode = FlowParseMode.NONE
        )

        message.flowKey shouldBe key
        message.stepKey shouldBe TestStep.key
        message.model shouldBe mapOf("foo" to "bar")
        message.inlineButtons shouldBe listOf(inlineButton)
        message.replyButtons shouldBe listOf(replyButton)
        message.parseMode shouldBe FlowParseMode.NONE
    }
})
