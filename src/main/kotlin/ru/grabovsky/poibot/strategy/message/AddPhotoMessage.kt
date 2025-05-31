package ru.grabovsky.poibot.strategy.message

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.MessageGenerateService
import ru.grabovsky.poibot.strategy.dto.VerificationDto

@Component
class AddPhotoMessage(messageGenerateService: MessageGenerateService) :
    AbstractSendMessage<VerificationDto>(messageGenerateService) {
}