package ru.grabovsky.poibot.strategy.processor.message

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.StateService

@Component
class AddPhotoProcessor(stateService: StateService) : MessageVerificationProcessor(stateService)