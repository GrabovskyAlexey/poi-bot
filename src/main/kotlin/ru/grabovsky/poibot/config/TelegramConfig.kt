package ru.grabovsky.poibot.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.grabovsky.poibot.strategy.dto.DataModel
import ru.grabovsky.poibot.strategy.message.AbstractSendMessage
import ru.grabovsky.poibot.strategy.processor.callback.CallbackProcessor
import ru.grabovsky.poibot.strategy.processor.message.MessageProcessor

@Configuration
class TelegramConfig<T : DataModel>(
    private val sendMessages: List<AbstractSendMessage<T>>,
    private val messageProcessors: List<MessageProcessor>,
    private val callbackProcessors: List<CallbackProcessor>
) {
    @Bean
    fun sendMessages() = sendMessages.associateBy { it.classStateCode() }
    @Bean
    fun messageProcessors() = messageProcessors.associateBy { it.classStateCode() }
    @Bean
    fun callbackProcessors() = callbackProcessors.associateBy { it.classStateCode() }
}