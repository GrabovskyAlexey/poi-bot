package ru.grabovsky.poibot.strategy.state

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.service.interfaces.StateService

@Component
class PlaceMenuState(
    private val stateService: StateService
) : State {
    override fun getNextState(user: User, chat: Chat): StateCode? {
        val state = stateService.getState(user, chat) ?: return null
        return runCatching {
            return@runCatching state.callbackData?. let { StateCode.valueOf(it) }
        }.onFailure {
            logger.warn { "Incorrect state data: ${state.callbackData}" }
        }.getOrNull()
    }

    companion object {
        val logger = KotlinLogging.logger {}
    }
}