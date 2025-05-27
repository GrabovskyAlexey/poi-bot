package ru.grabovsky.poibot.strategy.data

import org.springframework.stereotype.Repository
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.strategy.dto.DataModel
import ru.grabovsky.poibot.strategy.state.StateCode
import ru.grabovsky.poibot.util.CommonUtils.currentStateCode

@Repository
abstract class AbstractDataRepository<T: DataModel> {
    abstract fun getData(user: User, chat: Chat): T

    fun isAvailableForCurrentState(stateCode: StateCode)=
        this.currentStateCode("DataRepository") == stateCode


}