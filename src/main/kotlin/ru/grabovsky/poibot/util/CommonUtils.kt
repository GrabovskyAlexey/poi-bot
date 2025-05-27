package ru.grabovsky.poibot.util

import ru.grabovsky.poibot.strategy.state.StateCode

object CommonUtils {

    val REGEXP = "(?<=.)[A-Z]".toRegex()

    fun Any.currentStateCode(removeSuffix: String): StateCode {
        val stateCodeName = this
            .javaClass
            .simpleName
            .removeSuffix(removeSuffix)
            .replace(REGEXP, "_$0")
            .uppercase()

        return StateCode.valueOf(stateCodeName)
    }
}