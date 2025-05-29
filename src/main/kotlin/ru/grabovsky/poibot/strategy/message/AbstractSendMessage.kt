package ru.grabovsky.poibot.strategy.message

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.dto.InlineMarkupDataDto
import ru.grabovsky.poibot.dto.ReplyMarkupDto
import ru.grabovsky.poibot.service.interfaces.MessageGenerateService
import ru.grabovsky.poibot.strategy.dto.DataModel
import ru.grabovsky.poibot.util.CommonUtils.currentStateCode

@Component
abstract class AbstractSendMessage<T: DataModel?>(private val messageGenerateService: MessageGenerateService) {

    fun classStateCode() = this.currentStateCode("Message")

    fun message(data: T? = null): String = messageGenerateService.process(classStateCode(), data)

    fun inlineButtons(user: User, chat: Chat, data: T?): List<InlineMarkupDataDto> = emptyList()

    fun replyButtons(user: User, chat: Chat, data: T? = null): List<ReplyMarkupDto> = emptyList()

    fun isPermitted(user: User, chat: Chat): Boolean = true
}
