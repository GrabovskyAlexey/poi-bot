package ru.grabovsky.poibot.strategy.processor

import ru.grabovsky.poibot.util.CommonUtils.currentStateCode


interface Processor {
    fun classStateCode() = this.currentStateCode("Processor")
}
