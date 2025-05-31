package ru.grabovsky.poibot.strategy.message

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.MessageGenerateService
import ru.grabovsky.poibot.strategy.dto.VerificationDto

@Component
class AddAddressMessage(messageGenerateService: MessageGenerateService) :
    AbstractSendMessage<VerificationDto>(messageGenerateService) {
}