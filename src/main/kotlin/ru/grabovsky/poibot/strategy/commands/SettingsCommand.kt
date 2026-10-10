package ru.grabovsky.poibot.strategy.commands

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.ChatService
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowEngine
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKeys

@Component
class SettingsCommand(
    userService: UserService,
    flowEngine: FlowEngine,
    chatService: ChatService
) : AbstractCommand(Command.SETTINGS, FlowKeys.SETTINGS, userService, flowEngine, chatService)
