package ru.grabovsky.poibot.strategy.processor.message

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.StateService

@Component
class AddNameProcessor(stateService: StateService) : MessageVerificationProcessor(stateService)