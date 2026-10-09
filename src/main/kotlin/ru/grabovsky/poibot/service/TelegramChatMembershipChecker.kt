package ru.grabovsky.poibot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.grabovsky.poibot.service.interfaces.ChatMembershipChecker

@Component
class TelegramChatMembershipChecker(
    private val telegramClient: TelegramClient,
) : ChatMembershipChecker {

    override fun isMember(chatId: Long, userId: Long): Boolean? =
        statusOf(chatId, userId)?.let { it !in LEFT_STATUSES }

    override fun isAdmin(chatId: Long, userId: Long): Boolean =
        statusOf(chatId, userId) in ADMIN_STATUSES

    private fun statusOf(chatId: Long, userId: Long): String? =
        runCatching {
            telegramClient.execute(GetChatMember.builder().chatId(chatId).userId(userId).build()).status
        }.onFailure {
            logger.debug { "getChatMember failed for chat=$chatId user=$userId: ${it.message}" }
        }.getOrNull()

    private companion object {
        val logger = KotlinLogging.logger {}
        val LEFT_STATUSES = setOf("left", "kicked")
        val ADMIN_STATUSES = setOf("administrator", "creator")
    }
}
