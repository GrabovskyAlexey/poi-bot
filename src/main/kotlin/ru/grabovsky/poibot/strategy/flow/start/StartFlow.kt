package ru.grabovsky.poibot.strategy.flow.start

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.strategy.flow.core.AbstractStaticFlow
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKeys
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowStep

@Component
class StartFlow : AbstractStaticFlow(
    key = FlowKeys.START,
    step = StepKey.MAIN,
    bindingKey = MAIN_MESSAGE_BINDING,
    modelProvider = { context -> StartViewModel(context.user.userName ?: context.user.firstName) }
) {
    companion object {
        private const val MAIN_MESSAGE_BINDING = "start_main"
    }
}

data class StartViewModel(
    val username: String
)

enum class StepKey(override val key: String) : FlowStep {
    MAIN("main"),
}
