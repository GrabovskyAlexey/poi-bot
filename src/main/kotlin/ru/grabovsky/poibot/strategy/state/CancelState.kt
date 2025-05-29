package ru.grabovsky.poibot.strategy.state

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.service.interfaces.StateService

@Component
class CancelState: State {
    override fun getNextState(user: User, chat: Chat): StateCode? = StateCode.WAITING
}