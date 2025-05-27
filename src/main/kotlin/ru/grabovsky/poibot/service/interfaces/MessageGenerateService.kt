package ru.grabovsky.poibot.service.interfaces

import ru.grabovsky.poibot.strategy.state.StateCode


interface MessageGenerateService {
    fun process(state: StateCode, freemarkerData: Any? = null): String
}