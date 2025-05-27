package ru.grabovsky.poibot.config


import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import ru.grabovsky.poibot.strategy.message.AbstractSendMessage
import ru.grabovsky.poibot.strategy.state.StateCode
import kotlin.collections.associateBy


@ConfigurationProperties(prefix = "telegram")
class BotConfig(
    val token: String,
    val name: String,
) {
    @Bean
    fun telegramClient() = OkHttpTelegramClient(token)
}