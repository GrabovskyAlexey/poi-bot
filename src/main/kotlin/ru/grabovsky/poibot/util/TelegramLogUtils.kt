package ru.grabovsky.poibot.util

import com.fasterxml.jackson.databind.ObjectMapper
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup

/**
 * Утилиты для форматирования Telegram объектов в читаемый лог
 */
object TelegramLogUtils {

    /**
     * Форматирует входящий Update в компактную строку с деталями
     * Показывает детали для ВСЕХ типов событий, включая те, которые не обрабатываются
     */
    fun formatUpdate(update: Update): String {
        return when {
            update.hasMessage() -> "Update(message: ${formatMessage(update.message)})"
            update.hasCallbackQuery() -> "Update(callback: ${formatCallbackQuery(update.callbackQuery)})"
            update.hasMyChatMember() -> {
                val member = update.myChatMember
                "Update(chatMember: userId=${member.from.id}, chatId=${member.chat.id}, " +
                "oldStatus=${member.oldChatMember.status}, newStatus=${member.newChatMember.status})"
            }
            update.hasEditedMessage() -> "Update(editedMessage: ${formatMessage(update.editedMessage)})"
            update.hasChannelPost() -> {
                val post = update.channelPost
                "Update(channelPost: chatId=${post.chatId}, messageId=${post.messageId}, " +
                "text='${post.text?.take(100) ?: "no text"}')"
            }
            update.hasEditedChannelPost() -> {
                val post = update.editedChannelPost
                "Update(editedChannelPost: chatId=${post.chatId}, messageId=${post.messageId}, " +
                "text='${post.text?.take(100) ?: "no text"}')"
            }
            else -> "Update(type=unknown, updateId=${update.updateId})"
        }
    }

    /**
     * Форматирует входящее Message в компактную строку
     */
    fun formatMessage(message: Message): String {
        return buildString {
            append("Message(")
            append("messageId=${message.messageId}, ")
            append("userId=${message.from.id}, ")
            append("username=${message.from.userName}, ")
            append("chatId=${message.chatId}")
            message.text?.let { append(", text='${it.take(100)}'") }
            if (message.hasPhoto()) append(", hasPhoto=true")
            if (message.hasDocument()) append(", hasDocument=true")
            if (message.hasLocation()) append(", hasLocation=true")
            append(")")
        }
    }

    /**
     * Форматирует входящий CallbackQuery в компактную строку
     */
    fun formatCallbackQuery(callbackQuery: CallbackQuery): String {
        return buildString {
            append("CallbackQuery(")
            append("callbackId=${callbackQuery.id}, ")
            append("userId=${callbackQuery.from.id}, ")
            append("username=${callbackQuery.from.userName}, ")
            append("messageId=${callbackQuery.message?.messageId}")
            callbackQuery.data?.let { append(", data='$it'") }
            append(")")
        }
    }

    /**
     * Форматирует User в компактную строку
     */
    fun formatUser(user: User): String {
        return "User(userId=${user.id}, username=${user.userName}, firstName=${user.firstName}, languageCode=${user.languageCode})"
    }

    /**
     * Форматирует исходящее SendMessage в компактную строку
     */
    fun formatSendMessage(message: SendMessage, objectMapper: ObjectMapper): String {
        return buildString {
            append("SendMessage(")
            append("chatId=${message.chatId}, ")
            append("textLength=${message.text.length}, ")
            append("parseMode=${message.parseMode}")
            message.replyMarkup?.let {
                if (it is InlineKeyboardMarkup) {
                    append(", ")
                    append(formatInlineKeyboard(it, objectMapper))
                }
            }
            append(")")
        }
    }

    /**
     * Форматирует исходящее EditMessageText в компактную строку
     */
    fun formatEditMessage(message: EditMessageText, objectMapper: ObjectMapper): String {
        return buildString {
            append("EditMessage(")
            append("chatId=${message.chatId}, ")
            append("messageId=${message.messageId}, ")
            append("textLength=${message.text.length}, ")
            append("parseMode=${message.parseMode}")
            message.replyMarkup?.let {
                append(", ")
                append(formatInlineKeyboard(it, objectMapper))
            }
            append(")")
        }
    }

    /**
     * Форматирует InlineKeyboardMarkup в компактную строку с callback data
     */
    private fun formatInlineKeyboard(keyboard: InlineKeyboardMarkup, objectMapper: ObjectMapper): String {
        val rows = keyboard.keyboard.mapIndexed { rowIndex, row ->
            val buttons = row.mapIndexed { colIndex, button ->
                val callbackData = button.callbackData ?: "null"
                "'${button.text}'[$callbackData]"
            }.joinToString(", ")
            "row$rowIndex=[$buttons]"
        }.joinToString("; ")
        return "inlineButtons={$rows}"
    }
}
