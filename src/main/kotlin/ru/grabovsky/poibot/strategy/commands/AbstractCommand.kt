package ru.grabovsky.poibot.strategy.commands

import io.github.oshai.kotlinlogging.KotlinLogging
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.grabovsky.poibot.service.interfaces.ChatService
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowEngine
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKey
import ru.grabovsky.poibot.util.LocaleUtils

abstract class AbstractCommand(
    command: Command,
    protected val flowKey: FlowKey,
    protected val userService: UserService,
    private val flowEngine: FlowEngine,
    private val chatService: ChatService,
    val sortOrder: Int = command.order,
) : BotCommand(command.command, command.text), BotCommands {

    override fun prepare(user: User, chat: Chat, arguments: Array<out String>) {
        userService.createOrUpdateUser(user)
    }

    override fun execute(
        telegramClient: TelegramClient,
        user: User,
        chat: Chat,
        arguments: Array<out String>,
    ) {
        if (!chat.isUserChat) {
            executeInGroup(user, chat, arguments)
            return
        }
        logger.info { "Process flow ${flowKey.value} for user ${user.userName ?: user.firstName} with id ${user.id}" }
        runCatching {
            prepare(user, chat, arguments)
            val locale = LocaleUtils.resolve(userService.getUser(user.id))
            if (!flowEngine.start(flowKey, user, locale)) {
                logger.error { "Flow $flowKey not found, command processing aborted" }
            }
        }.onFailure { error ->
            logger.warn { "Error process flow ${flowKey.value} for user ${user.userName ?: user.firstName} with id ${user.id} with error: $error, stacktrace: ${error.stackTrace}" }
        }
    }

    /**
     * Команда вызвана в группе. Диалоги в группах не ведём: фиксируем, что пользователь состоит в группе.
     * Команды чтения для групп переопределяют этот метод (этап 2).
     */
    protected open fun executeInGroup(user: User, chat: Chat, arguments: Array<out String>) {
        runCatching {
            userService.createOrUpdateUser(user)
            chatService.registerChat(chat)
            chatService.linkUser(user.id, chat.id)
        }.onFailure { error ->
            logger.warn { "Error registering group chat ${chat.id} for user ${user.id}: ${error.message}" }
        }
    }

    companion object {
        val logger = KotlinLogging.logger {}
    }
}
