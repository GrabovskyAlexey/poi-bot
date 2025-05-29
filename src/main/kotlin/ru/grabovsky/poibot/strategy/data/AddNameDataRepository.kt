package ru.grabovsky.poibot.strategy.data

import org.springframework.stereotype.Repository
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.strategy.dto.VerificationDto

@Repository
class AddNameDataRepository: AbstractDataRepository<VerificationDto>() {
    override fun getData(
        user: User,
        chat: Chat
    ):VerificationDto {
        return VerificationDto(true)
    }
}