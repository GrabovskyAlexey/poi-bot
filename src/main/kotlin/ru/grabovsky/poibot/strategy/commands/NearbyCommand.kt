package ru.grabovsky.poibot.strategy.commands

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.ChatService
import ru.grabovsky.poibot.service.interfaces.GroupPlacesService
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowEngine
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKeys

@Component
class NearbyCommand(
    userService: UserService,
    flowEngine: FlowEngine,
    chatService: ChatService,
    private val groupPlacesService: GroupPlacesService,
) : AbstractCommand(Command.NEARBY, FlowKeys.NEARBY, userService, flowEngine, chatService) {
    override fun executeInGroup(user: User, chat: Chat, messageId: Int?, arguments: Array<out String>) =
        groupPlacesService.askNearbyLocation(chat, user, messageId)
}
