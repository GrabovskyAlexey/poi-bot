package ru.grabovsky.poibot.strategy.state

import ru.grabovsky.poibot.strategy.state.StateAction.*

enum class StateCode(val action: StateAction, val pause: Boolean = true, val template: String? = null, val markType: MarkType = MarkType.NONE) {
    START(SEND_MESSAGE, false),
    WAITING(NOTHING),
    PLACE_MENU(SEND_MESSAGE, markType = MarkType.UPDATE),
    ADD_PHOTO(SEND_MESSAGE, markType = MarkType.DELETE),
    ADD_NAME(SEND_MESSAGE, markType = MarkType.DELETE),
    ADD_DESC(SEND_MESSAGE, markType = MarkType.DELETE),
    ADD_GEO(SEND_MESSAGE, markType = MarkType.DELETE),
    ADD_ADDRESS(SEND_MESSAGE,markType = MarkType.DELETE),
    VERIFY(VERIFICATION, false),
    VERIFICATION_ERROR(SEND_MESSAGE, false, markType = MarkType.DELETE),
    VERIFICATION_SUCCESS(DELETE_MESSAGES,false),
    UPDATE_ADD_MENU(UPDATE_MESSAGE, markType = MarkType.UPDATE),
    COMPLETE(UPDATE_MESSAGE, false, markType = MarkType.UPDATE),
    CANCEL(SEND_MESSAGE, false),
    CLEAR_MENU(DELETE_MESSAGES,false),
}

enum class StateAction {
    SEND_MESSAGE,
    UPDATE_MESSAGE,
    DELETE_MESSAGES,
    VERIFICATION,
    NOTHING
}

enum class MarkType{
    DELETE, UPDATE, NONE
}