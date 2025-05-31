package ru.grabovsky.poibot.strategy.state

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.service.interfaces.StateService

@Component
class PlaceMenuState(stateService: StateService) : AbstractCallbackState(stateService)