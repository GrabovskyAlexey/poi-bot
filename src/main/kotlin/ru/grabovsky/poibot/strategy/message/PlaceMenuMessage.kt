package ru.grabovsky.poibot.strategy.message

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.MessageGenerateService

@Component
class PlaceMenuMessage(messageGenerateService: MessageGenerateService) : AbstractMenuMessage(messageGenerateService)