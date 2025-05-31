package ru.grabovsky.poibot.strategy.processor.callback

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.entity.StateEntity
import ru.grabovsky.poibot.mapper.PoiMapper
import ru.grabovsky.poibot.service.interfaces.PoiService
import ru.grabovsky.poibot.service.interfaces.StateService
import ru.grabovsky.poibot.service.interfaces.VerificationService
import ru.grabovsky.poibot.strategy.state.StateCode

@Component
abstract class PoiMenuProcessor(
    private val stateService: StateService,
    private val poiService: PoiService,
    private val verificationService: VerificationService
) : CallbackProcessor {
    @Transactional
    override fun execute(user: User, chat: Chat, callbackQuery: CallbackQuery): ExecuteStatus {
        val state =
            stateService.getState(user, chat)
        state
            .apply { this.callbackData = callbackQuery.data }

        runCatching {
            val callbackState = StateCode.valueOf(callbackQuery.data)
            when (callbackState) {
                StateCode.COMPLETE -> processCompleteCallback(state)
                StateCode.CANCEL -> processCancelCallback(state)
                else -> return@runCatching
            }
        }
        stateService.saveState(state)
        return ExecuteStatus.FINAL
    }

    private fun processCompleteCallback(entity: StateEntity) {
        val poi = requireNotNull(entity.poiData?.let {
            PoiMapper.mapToPoiEntity(
                it,
                entity.userId,
                entity.chatId
            )
        }) { "Poi must not be null" }
        poiService.savePoi(poi)
//        entity.poiData = null
        verificationService.clearVerifyRequest(entity)
    }

    private fun processCancelCallback(entity: StateEntity) {
        entity.poiData = null
        entity.updateMessageId?. let {
            entity.deletedMessages.add(it)
            entity.updateMessageId = null
        }
        verificationService.clearVerifyRequest(entity)

    }
}