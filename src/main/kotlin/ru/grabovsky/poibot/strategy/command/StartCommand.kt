package ru.grabovsky.poibot.strategy.command

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.event.TelegramStateEvent
import ru.grabovsky.poibot.service.interfaces.ChatService
import ru.grabovsky.poibot.service.interfaces.UserService

@Component
class StartCommand(
    private val eventPublisher: ApplicationEventPublisher,
    private val userService: UserService,
    private val chatService: ChatService
) : AbstractCommand(Command.START, eventPublisher) {
    override fun prepare(user: User, chat: Chat, arguments: Array<out String>) {
        userService.createOrUpdateUser(user)
        chatService.createOrUpdateChat(chat)
        eventPublisher.publishEvent(TelegramStateEvent(user, chat, classStateCode()))
    }
}