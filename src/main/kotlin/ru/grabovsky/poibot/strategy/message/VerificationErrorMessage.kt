package ru.grabovsky.poibot.strategy.message

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.MessageGenerateService
import ru.grabovsky.poibot.strategy.dto.DataModel

@Component
class VerificationErrorMessage(messageGenerateService: MessageGenerateService) :
    AbstractSendMessage<DataModel>(messageGenerateService) {
}