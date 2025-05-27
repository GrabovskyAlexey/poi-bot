package ru.grabovsky.poibot.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.grabovsky.poibot.strategy.dto.DataModel
import ru.grabovsky.poibot.strategy.message.AbstractSendMessage
import ru.grabovsky.poibot.strategy.state.StateCode

@Configuration
class TelegramConfig<T : DataModel>(
    private val sendMessages: List<AbstractSendMessage<T>>,
) {
    @Bean
    fun sendMessages() = sendMessages.associateBy { it.classStepCode() }
}