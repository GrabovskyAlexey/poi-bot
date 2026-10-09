package ru.grabovsky.poibot.strategy.commands

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.ChatService
import ru.grabovsky.poibot.service.interfaces.SharingService
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowEngine
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKey
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKeys

@Component
class StartCommand(
    userService: UserService,
    flowEngine: FlowEngine,
    chatService: ChatService
) : AbstractCommand(Command.START, FlowKeys.START, userService, flowEngine, chatService) {

    /** Deep link `/start sp_<token>` открывает карточку места, которым поделились. */
    override fun resolveStart(arguments: Array<out String>): Pair<FlowKey, String?> {
        val payload = arguments.firstOrNull()
        return if (payload != null && payload.startsWith(SharingService.START_PREFIX)) {
            FlowKeys.SHARED to payload
        } else {
            super.resolveStart(arguments)
        }
    }
}
