package ru.grabovsky.poibot.strategy.context

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.dto.MessageModelDto
import ru.grabovsky.poibot.strategy.data.AbstractDataRepository
import ru.grabovsky.poibot.strategy.dto.DataModel
import ru.grabovsky.poibot.strategy.message.AbstractSendMessage
import ru.grabovsky.poibot.strategy.state.StateCode

@Component
class MessageContext<T : DataModel>(
    private val sendMessages: Map<StateCode, AbstractSendMessage<T>>,
    private val abstractDataRepository: List<AbstractDataRepository<T>>
) {
    fun getMessage(user: User, chat: Chat, stateCode: StateCode): MessageModelDto? {
        return sendMessages[stateCode]
            ?.takeIf { it.isPermitted(user, chat) }
            ?.let {
                val data = getData(user, chat, stateCode)
                MessageModelDto(
                    message = it.message(data),
                    inlineButtons = it.inlineButtons(user, chat, data),
                    replyButtons = it.replyButtons(user, chat, data)
                )
            }
    }

    private fun getData(user: User, chat: Chat, stateCode: StateCode) =
        abstractDataRepository.firstOrNull { it.isAvailableForCurrentState(stateCode) }?.getData(user, chat)
}