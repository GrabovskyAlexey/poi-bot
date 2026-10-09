package ru.grabovsky.poibot.strategy.commands

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.ChatService
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowEngine
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKeys

@Component
class AddCommand(
    userService: UserService,
    flowEngine: FlowEngine,
    chatService: ChatService
) : AbstractCommand(Command.ADD, FlowKeys.ADD_PLACE, userService, flowEngine, chatService)
