package ru.grabovsky.poibot.strategy.state

import ru.grabovsky.poibot.strategy.state.StateAction.*

enum class StateCode(val action: StateAction, val pause: Boolean = true, val template: String? = null) {
    START(SEND_MESSAGE, false),
    WAITING(NOTHING),
    PLACE_MENU(SEND_MESSAGE),
    ADD_PHOTO(SEND_MESSAGE),
    ADD_NAME(SEND_MESSAGE),
    ADD_DESC(SEND_MESSAGE),
    ADD_GEO(SEND_MESSAGE),
    ADD_ADDRESS(SEND_MESSAGE),
    COMPLETE(NOTHING),
}

enum class StateAction {
    SEND_MESSAGE,
    NOTHING
}