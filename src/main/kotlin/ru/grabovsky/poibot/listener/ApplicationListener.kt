package ru.grabovsky.poibot.listener

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import ru.grabovsky.poibot.event.TelegramStateEvent
import ru.grabovsky.poibot.event.TelegramEvent
import ru.grabovsky.poibot.event.TelegramReceiveMessageEvent
import ru.grabovsky.poibot.service.interfaces.MessageService
import ru.grabovsky.poibot.service.interfaces.StateService

@Component
class ApplicationListener(
    private val messageService: MessageService,
    private val stateService: StateService
) {
    @EventListener
    fun onTelegramEvent(event: TelegramEvent) {
        when (event) {
            is TelegramReceiveMessageEvent -> processMessageEvent(event)
            is TelegramStateEvent -> processCommandEvent(event)
        }
    }

    fun processMessageEvent(event: TelegramReceiveMessageEvent) {
//        println("Message: ${event.text}")
    }

    fun processCommandEvent(event: TelegramStateEvent) {
        stateService.updateState(event.user, event.chat, event.stateCode)
        messageService.sendMessageToBot(event.user, event.chat, event.stateCode)
    }
}
