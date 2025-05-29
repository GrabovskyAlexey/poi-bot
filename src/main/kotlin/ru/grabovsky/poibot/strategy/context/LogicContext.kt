package ru.grabovsky.poibot.strategy.context

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.strategy.processor.callback.CallbackProcessor
import ru.grabovsky.poibot.strategy.processor.callback.ExecuteStatus
import ru.grabovsky.poibot.strategy.processor.message.MessageProcessor
import ru.grabovsky.poibot.strategy.state.StateCode

@Component
class LogicContext(
    private val messageProcessors: Map<StateCode, MessageProcessor>,
    private val callbackProcessors: Map<StateCode, CallbackProcessor>
) {

    fun execute(user: User, chat: Chat, message: Message, stateCode: StateCode) {
        messageProcessors[stateCode]?.execute(user, chat, message = message)
    }

    fun execute(user: User, chat: Chat, callbackQuery: CallbackQuery, stateCode: StateCode): ExecuteStatus {
        return callbackProcessors[stateCode]
            ?.execute(user, chat, callbackQuery = callbackQuery)
            ?: throw IllegalStateException("Callback not found with state: $stateCode")
    }

}