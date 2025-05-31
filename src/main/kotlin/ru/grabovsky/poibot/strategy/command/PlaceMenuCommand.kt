package ru.grabovsky.poibot.strategy.command

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.service.interfaces.StateService

@Component
class PlaceMenuCommand(
    eventPublisher: ApplicationEventPublisher,
    private val stateService: StateService
) : AbstractCommand(Command.ADD, eventPublisher) {
    override fun prepare(user: User, chat: Chat, arguments: Array<out String>) {
        stateService.getState(user, chat)
            .apply { this.poiData = null }
            .also {stateService.saveState(it)}

    }
}