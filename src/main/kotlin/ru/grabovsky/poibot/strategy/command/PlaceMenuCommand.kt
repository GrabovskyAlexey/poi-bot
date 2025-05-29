package ru.grabovsky.poibot.strategy.command

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.entity.PoIEntity
import ru.grabovsky.poibot.event.TelegramStateEvent
import ru.grabovsky.poibot.service.interfaces.ChatService
import ru.grabovsky.poibot.service.interfaces.StateService
import ru.grabovsky.poibot.service.interfaces.UserService

@Component
class PlaceMenuCommand(
    eventPublisher: ApplicationEventPublisher,
    private val objectMapper: ObjectMapper,
    private val stateService: StateService
) : AbstractCommand(Command.ADD, eventPublisher) {
    override fun prepare(
        user: User,
        chat: Chat,
        arguments: Array<out String>
    ) {
        stateService.getState(user, chat)?.let {
            it.poiData = objectMapper.writeValueAsString(PoIEntity(
                chatId = chat.id,
                userId = user.id
            ))
            stateService.saveState(it)
        }
    }
}