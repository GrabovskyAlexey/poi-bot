package ru.grabovsky.poibot.strategy.commands

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.grabovsky.poibot.service.interfaces.ChatService
import ru.grabovsky.poibot.service.interfaces.UnknownCommandService
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowEngine
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKeys

/** `/admin` скрыта из меню бота и для всех, кроме администраторов (`UserProfile.isAdmin`), неотличима от несуществующей команды. */
@Component
class AdminCommand(
    userService: UserService,
    flowEngine: FlowEngine,
    chatService: ChatService,
    private val unknownCommandService: UnknownCommandService,
) : AbstractCommand(Command.ADMIN, FlowKeys.ADMIN, userService, flowEngine, chatService) {

    /** Не администратору отвечаем как на любую несуществующую команду, чтобы не выдать, что `/admin` есть. */
    override fun processMessage(telegramClient: TelegramClient, message: Message, arguments: Array<out String>) {
        if (userService.getUser(message.from.id)?.profile?.isAdmin != true) {
            logger.info { "Treat /admin from non-admin user ${message.from.id} as unknown command" }
            unknownCommandService.reply(message)
            return
        }
        super.processMessage(telegramClient, message, arguments)
    }

    /** В группах команду не обрабатываем и чат не регистрируем. */
    override fun executeInGroup(user: User, chat: Chat, messageId: Int?, arguments: Array<out String>) = Unit
}
