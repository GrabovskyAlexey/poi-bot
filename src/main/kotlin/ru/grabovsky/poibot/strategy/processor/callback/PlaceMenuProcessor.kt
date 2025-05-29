package ru.grabovsky.poibot.strategy.processor.callback

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.entity.PoIEntity
import ru.grabovsky.poibot.entity.StateEntity
import ru.grabovsky.poibot.service.interfaces.PoiService
import ru.grabovsky.poibot.service.interfaces.StateService
import ru.grabovsky.poibot.strategy.state.StateCode

@Component
class PlaceMenuProcessor(
    private val stateService: StateService,
    private val poiService: PoiService,
    private val objectMapper: ObjectMapper
) : CallbackProcessor {
    @Transactional
    override fun execute(user: User, chat: Chat, callbackQuery: CallbackQuery): ExecuteStatus {
        val state =
            stateService.getState(user, chat) ?: throw IllegalStateException("State not found in database")
        state
            .apply { this.callbackData = callbackQuery.data }

        runCatching {
            val callbackState = StateCode.valueOf(callbackQuery.data)
            when(callbackState) {
                StateCode.COMPLETE -> processCompleteCallback(state)
                StateCode.CANCEL -> processCancelCallback(user, chat, state)
                else -> return@runCatching
            }
        }
        stateService.saveState(state)
        return ExecuteStatus.FINAL
    }

    private fun processCompleteCallback(entity: StateEntity) {
        val poi = objectMapper.readValue(entity.poiData, PoIEntity::class.java)
        poiService.savePoi(poi)
    }

    private fun processCancelCallback(user: User, chat: Chat, entity: StateEntity) {
        entity.poiData = objectMapper.writeValueAsString(
            PoIEntity(
                chatId = chat.id,
                userId = user.id
            )
        )
    }
}