package ru.grabovsky.poibot.strategy.data

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Repository
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.entity.PoIEntity
import ru.grabovsky.poibot.service.interfaces.StateService
import ru.grabovsky.poibot.strategy.dto.PlaceMenuDto
import ru.grabovsky.poibot.strategy.dto.VerificationDto

@Repository
class PlaceMenuDataRepository(
    private val stateService: StateService,
    private val objectMapper: ObjectMapper
): AbstractDataRepository<PlaceMenuDto>() {
    override fun getData(
        user: User,
        chat: Chat
    ): PlaceMenuDto {
        val state = requireNotNull(stateService.getState(user, chat)) { "State must not be null" }
        val poiData = requireNotNull(state.poiData) { "PoiData must not be null" }
        val point = objectMapper.readValue(poiData, PoIEntity::class.java)
        return PlaceMenuDto(point)
    }
}