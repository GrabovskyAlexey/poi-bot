package ru.grabovsky.poibot.service

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.entity.StateEntity
import ru.grabovsky.poibot.repository.StateRepository
import ru.grabovsky.poibot.service.interfaces.ChatService
import ru.grabovsky.poibot.service.interfaces.StateService
import ru.grabovsky.poibot.service.interfaces.UserService
import ru.grabovsky.poibot.strategy.state.StateCode

@Service
class StateServiceImpl(
    private val userService: UserService,
    private val chatService: ChatService,
    private val stateRepository: StateRepository
) : StateService {

    @Transactional
    override fun updateState(user: User, chat: Chat, code: StateCode, callbackData: String?) {
        userService.createOrUpdateUser(user)
        chatService.createOrUpdateChat(chat)
        (
                stateRepository.findByUserIdAndChatId(user.id, chat.id) ?: StateEntity(
                    userId = user.id,
                    chatId = chat.id
                )
                ).apply {
                this.state = code
                this.callbackData = callbackData
            }.also { stateRepository.saveAndFlush(it) }
    }
}