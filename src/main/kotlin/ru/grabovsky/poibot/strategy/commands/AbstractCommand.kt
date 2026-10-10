package ru.grabovsky.poibot.strategy.commands

import io.github.oshai.kotlinlogging.KotlinLogging
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.message.Message
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

    /** В группах нужен id сообщения команды (ответ «реплаем» и селективный ForceReply). */
    override fun processMessage(telegramClient: TelegramClient, message: Message, arguments: Array<out String>) {
        if (!message.chat.isUserChat) {
            executeInGroup(message.from, message.chat, message.messageId, arguments)
            return
        }
        super.processMessage(telegramClient, message, arguments)
        if (userService.getUser(message.from.id)?.profile?.settings?.cleanChat != false) {
            deleteCommandMessage(telegramClient, message)
        }
    }

    override fun execute(
        telegramClient: TelegramClient,
        user: User,
        chat: Chat,
        arguments: Array<out String>,
    ) {
        if (!chat.isUserChat) {
            executeInGroup(user, chat, null, arguments)
            return
        }
        logger.info { "Process flow ${flowKey.value} for user ${user.userName ?: user.firstName} with id ${user.id}" }
        runCatching {
            prepare(user, chat, arguments)
            val locale = LocaleUtils.resolve(userService.getUser(user.id))
            val (startKey, startArgs) = resolveStart(arguments)
            if (!flowEngine.start(startKey, user, locale, startArgs)) {
                logger.error { "Flow $startKey not found, command processing aborted" }
            }
        }.onFailure { error ->
            logger.warn { "Error process flow ${flowKey.value} for user ${user.userName ?: user.firstName} with id ${user.id} with error: $error, stacktrace: ${error.stackTrace}" }
        }
    }

    /** В личке сообщение с командой после обработки только засоряет чат; не получилось удалить - не страшно. */
    private fun deleteCommandMessage(telegramClient: TelegramClient, message: Message) {
        runCatching {
            telegramClient.execute(DeleteMessage.builder().chatId(message.chatId).messageId(message.messageId).build())
        }.onFailure { error ->
            logger.debug { "Command message ${message.messageId} not deleted: ${error.message}" }
        }
    }

    /** Какой flow и с какими аргументами запускать (по умолчанию — свой, без аргументов). */
    protected open fun resolveStart(arguments: Array<out String>): Pair<FlowKey, String?> = flowKey to null

    /**
     * Команда вызвана в группе. Диалоги в группах не ведём: фиксируем, что пользователь состоит в группе.
     * Команды чтения для групп переопределяют этот метод.
     */
    protected open fun executeInGroup(user: User, chat: Chat, messageId: Int?, arguments: Array<out String>) {
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
