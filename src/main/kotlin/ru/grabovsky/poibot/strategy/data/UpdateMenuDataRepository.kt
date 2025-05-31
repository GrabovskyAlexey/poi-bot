package ru.grabovsky.poibot.strategy.data

import org.springframework.stereotype.Repository
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.entity.Poi
import ru.grabovsky.poibot.service.interfaces.StateService
import ru.grabovsky.poibot.strategy.dto.PlaceMenuDto

@Repository
abstract class UpdateMenuDataRepository(
    private val stateService: StateService
    ): AbstractDataRepository<PlaceMenuDto>() {
    override fun getData(
        user: User,
        chat: Chat
    ): PlaceMenuDto {
        val state = stateService.getState(user, chat)
        val poi = state.poiData ?: Poi()
        return PlaceMenuDto(poi)
    }
}