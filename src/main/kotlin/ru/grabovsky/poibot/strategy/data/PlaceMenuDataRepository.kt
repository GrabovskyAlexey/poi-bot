package ru.grabovsky.poibot.strategy.data

import org.springframework.stereotype.Repository
import ru.grabovsky.poibot.service.interfaces.StateService

@Repository
class PlaceMenuDataRepository(stateService: StateService): UpdateMenuDataRepository(stateService)