package ru.grabovsky.poibot.strategy.commands

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowEngine
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKeys

@Component
class StartCommand(
    userService: UserService,
    flowEngine: FlowEngine
) : AbstractCommand(Command.START, FlowKeys.START, userService, flowEngine)
