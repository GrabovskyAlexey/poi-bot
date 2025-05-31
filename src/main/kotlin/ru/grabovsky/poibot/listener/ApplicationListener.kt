package ru.grabovsky.poibot.listener

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import ru.grabovsky.poibot.event.TelegramEvent
import ru.grabovsky.poibot.event.TelegramReceiveCallbackEvent
import ru.grabovsky.poibot.event.TelegramReceiveMessageEvent
import ru.grabovsky.poibot.event.TelegramStateEvent
import ru.grabovsky.poibot.service.interfaces.StateService
import ru.grabovsky.poibot.service.interfaces.TelegramBotService
import ru.grabovsky.poibot.strategy.context.LogicContext
import ru.grabovsky.poibot.strategy.context.StateContext
import ru.grabovsky.poibot.strategy.processor.callback.ExecuteStatus

@Component
class ApplicationListener(
    private val messageService: TelegramBotService,
    private val stateService: StateService,
    private val stateContext: StateContext,
    private val logicContext: LogicContext,
) {
    @EventListener
    fun onTelegramEvent(event: TelegramEvent) {
        when (event) {
            is TelegramReceiveMessageEvent -> processMessageEvent(event)
            is TelegramStateEvent -> processStateEvent(event)
            is TelegramReceiveCallbackEvent -> processCallbackEvent(event)
        }
    }

    fun processMessageEvent(event: TelegramReceiveMessageEvent) {
        logicContext.execute(event.user, event.chat, event.message, event.stateCode)

        stateContext.next(event.user, event.chat, event.stateCode)?.let {
            processStateEvent(TelegramStateEvent(event.user, event.chat, it))
        }
    }

    fun processStateEvent(event: TelegramStateEvent) {
        logger.debug { "Current state: ${event.stateCode.name}, pause: ${event.stateCode.pause}" }
        stateService.updateState(event.user, event.chat, event.stateCode)
        messageService.processState(event.user, event.chat, event.stateCode)
    }

    fun processCallbackEvent(event: TelegramReceiveCallbackEvent) {
        when (logicContext.execute(event.user, event.chat, event.callback, event.stateCode)) {
            ExecuteStatus.FINAL -> { stateContext.next(event.user, event.chat, event.stateCode) }
            ExecuteStatus.NOTHING -> null
        }?.let {
            processStateEvent(TelegramStateEvent(event.user, event.chat, it))
        }
    }

    companion object {
        val logger = KotlinLogging.logger {}
    }
}
