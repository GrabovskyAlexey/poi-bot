package ru.grabovsky.poibot.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient

@ConfigurationProperties(prefix = "telegram")
class BotConfig(
    val token: String,
    val name: String,
) {
    @Bean
    fun telegramClient(): OkHttpTelegramClient {
        validateToken()
        return OkHttpTelegramClient(token)
    }

    /** Ловит типичные ошибки конфигурации (пустое значение, кавычки, пробелы) с понятным сообщением, без вывода секрета. */
    private fun validateToken() {
        val problems = buildList {
            if (token.startsWith("\${")) {
                add("переменная окружения не передана приложению (в токен попал плейсхолдер $token): проверьте, что IDEA загружает .env")
            } else if (token.isBlank()) {
                add("токен пустой (переменная TELEGRAM_BOT_TOKEN не передана или пуста)")
            }
            if (token != token.trim()) add("в токене есть пробелы или переводы строк по краям")
            if (token.any { it == '"' || it == '\'' }) add("в токене есть кавычки")
            if (token.isNotBlank() && !TOKEN_FORMAT.matches(token.trim().trim('"', '\''))) {
                add("формат не похож на токен BotFather (ожидается <id>:<секрет>)")
            }
        }
        // id бота — публичная часть токена, её безопасно писать в лог
        logger.info { "Telegram: botName=$name, tokenBotId=${token.substringBefore(':')}, tokenLength=${token.length}" }
        check(problems.isEmpty()) { "Некорректный Telegram-токен: ${problems.joinToString("; ")}" }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
        val TOKEN_FORMAT = Regex("^\\d+:[A-Za-z0-9_-]{30,}$")
    }
}
