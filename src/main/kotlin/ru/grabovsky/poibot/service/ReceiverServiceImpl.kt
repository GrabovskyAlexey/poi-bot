package ru.grabovsky.poibot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.service.interfaces.ReceiverService

@Service
class ReceiverServiceImpl : ReceiverService {
    override fun execute(update: Update) {
        when {
            update.hasCallbackQuery() -> processCallback(update.callbackQuery)
            update.hasMessage() -> processMessage(update.message)
        }
    }

    private fun processMessage(message: Message) {
        logger.info { "Received message: $message" }
    }

    private fun processCallback(callbackQuery: CallbackQuery) {
        logger.info { "Received callback: $callbackQuery" }
    }

    companion object {
        val logger = KotlinLogging.logger {}
    }
}