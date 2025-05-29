package ru.grabovsky.poibot.strategy.processor.message

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.entity.PoIEntity
import ru.grabovsky.poibot.service.interfaces.StateService
import ru.grabovsky.poibot.strategy.processor.message.MessageProcessor

@Component
class AddPhotoProcessor(
    private val stateService: StateService,
    private val objectMapper: ObjectMapper
) : MessageProcessor {
    override fun execute(
        user: User,
        chat: Chat,
        message: Message
    ) {
//        val state = stateService.getState(user, chat)?: return
//        val poiData = state.poiData ?: return
//        val point = objectMapper.readValue(poiData, PoIEntity::class.java)
//        point.photoId = message.photo.last().fileUniqueId
//        state.poiData = objectMapper.writeValueAsString(point)
//        stateService.saveState(state)
    }
}