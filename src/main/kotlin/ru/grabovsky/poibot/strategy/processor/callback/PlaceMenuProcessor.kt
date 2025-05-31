package ru.grabovsky.poibot.strategy.processor.callback

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.PoiService
import ru.grabovsky.poibot.service.interfaces.StateService
import ru.grabovsky.poibot.service.interfaces.VerificationService

@Component
class PlaceMenuProcessor(
    stateService: StateService,
    poiService: PoiService,
    verificationService: VerificationService
) : PoiMenuProcessor(stateService, poiService, verificationService)