package ru.grabovsky.poibot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.grabovsky.poibot.service.interfaces.ChatCleanupPolicy
import ru.grabovsky.poibot.service.interfaces.I18nService
import ru.grabovsky.poibot.service.interfaces.UnknownCommandService
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.util.LocaleUtils
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Service
class UnknownCommandServiceImpl(
    private val telegramClient: TelegramClient,
    private val userService: UserService,
    private val i18n: I18nService,
    private val cleanup: ChatCleanupPolicy,
    private val scheduler: ScheduledExecutorService = defaultScheduler(),
) : UnknownCommandService {

    override fun reply(message: Message) {
        if (!message.chat.isUserChat) return
        val chatId = message.chatId
        runCatching {
            val locale = LocaleUtils.resolve(message.from?.let { userService.getUser(it.id) })
            val reply = telegramClient.execute(SendMessage(chatId.toString(), i18n.i18n("alerts.command.unknown", locale)))
            scheduleDeletion(chatId, listOf(message.messageId, reply.messageId))
        }.onFailure {
            logger.warn { "Failed to answer unknown command in chat $chatId: ${it.message}" }
            scheduleDeletion(chatId, listOf(message.messageId))
        }
    }

    /** Как и остальные служебные сообщения, удаляются только при включённом автоудалении в `/settings`. */
    private fun scheduleDeletion(chatId: Long, messageIds: List<Int>) {
        if (!cleanup.enabled(chatId)) return
        scheduler.schedule({
            runCatching {
                telegramClient.execute(DeleteMessages.builder().chatId(chatId).messageIds(messageIds).build())
            }.onFailure { logger.debug { "Could not delete unknown command messages $messageIds: ${it.message}" } }
        }, UnknownCommandService.DELETE_AFTER_SECONDS, TimeUnit.SECONDS)
    }

    private companion object {
        val logger = KotlinLogging.logger {}

        fun defaultScheduler(): ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "unknown-command-delete").apply { isDaemon = true }
            }
    }
}
