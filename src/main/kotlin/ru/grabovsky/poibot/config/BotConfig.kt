package ru.grabovsky.poibot.config


import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient


@ConfigurationProperties(prefix = "telegram")
class BotConfig(
    val token: String,
    val name: String,
) {
    @Bean
    fun telegramClient() = OkHttpTelegramClient(token)
}