package ru.grabovsky.poibot.strategy.message

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.dto.ReplyMarkupDto
import ru.grabovsky.poibot.service.interfaces.MessageGenerateService
import ru.grabovsky.poibot.strategy.dto.VerificationDto

@Component
class AddGeoMessage(messageGenerateService: MessageGenerateService) :
    AbstractSendMessage<VerificationDto>(messageGenerateService) {
    override fun replyButtons(
        user: User,
        chat: Chat,
        data: VerificationDto?
    ): List<ReplyMarkupDto> {
        return listOf(ReplyMarkupDto(text = "Укажите геопозицию", requestLocation = true))
    }
}