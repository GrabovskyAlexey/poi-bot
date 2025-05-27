package ru.grabovsky.poibot.bot

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.extensions.bots.commandbot.CommandLongPollingTelegramBot
import org.telegram.telegrambots.longpolling.BotSession
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.grabovsky.poibot.strategy.command.AbstractCommand
import ru.grabovsky.poibot.config.BotConfig
import ru.grabovsky.poibot.service.interfaces.ReceiverService

@Component
class PoiBot(
    private val config: BotConfig,
    private val client: TelegramClient,
    private val receiverService: ReceiverService,
    commands: List<AbstractCommand>
): SpringLongPollingBot, CommandLongPollingTelegramBot(client, true, { config.name }) {

    init {
        registerAll(*commands.toTypedArray())
        SetMyCommands(commands.map { BotCommand(it.commandIdentifier, it.description) })
            .also { client.execute(it) }
    }
    override fun processNonCommandUpdate(update: Update) {
        receiverService.execute(update)
    }

    override fun getBotToken() = config.token

    override fun getUpdatesConsumer() = this

    @AfterBotRegistration
    fun afterRegistration(botSession: BotSession) {
        logger.info {"Registered bot running state is: ${botSession.isRunning}" }
    }

    companion object {
        val logger = KotlinLogging.logger {}
    }
}