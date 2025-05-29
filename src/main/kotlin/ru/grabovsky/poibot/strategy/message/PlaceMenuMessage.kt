package ru.grabovsky.poibot.strategy.message

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.dto.InlineMarkupDataDto
import ru.grabovsky.poibot.service.interfaces.MessageGenerateService
import ru.grabovsky.poibot.strategy.dto.PlaceMenuDto

@Component
class PlaceMenuMessage(
    messageGenerateService: MessageGenerateService) :
    AbstractSendMessage<PlaceMenuDto>(messageGenerateService) {
    override fun inlineButtons(
        user: User,
        chat: Chat,
        data: PlaceMenuDto?
    ): List<InlineMarkupDataDto> {
        return listOf(
            InlineMarkupDataDto(
                0,
                getButtonText(data?.poi?.name, "✏\uFE0F", "название"),
                "ADD_NAME"
            ),
            InlineMarkupDataDto(
                1,
                getButtonText(data?.poi?.description, "\uD83D\uDCDD", "описание"),
                "ADD_DESC"
            ),
//            InlineMarkupDataDto(
//                2,
//                getButtonText(data?.poi?.photoId, "\uD83D\uDCF8", "фото"),
//                "ADD_PHOTO"
//            ), TODO Вернуть после решения проблемы хранения фото
            InlineMarkupDataDto(
                3,
                getButtonText(data?.poi?.address, "\uD83D\uDCEA", "адрес"),
                "ADD_ADDRESS"
            ),
            InlineMarkupDataDto(
                4,
                getButtonText(data?.poi?.location, "\uD83D\uDCCD","геопозицию"),
                "ADD_GEO"
            ),
            *getAdditionalButtons(data).toTypedArray()
        )
    }

    private fun getButtonText(data: Any?, prefix: String,  postfix: String) = data?.let { "${prefix}Изменить $postfix" } ?: "${prefix}Добавить $postfix"

    private fun getAdditionalButtons(data: PlaceMenuDto?): List<InlineMarkupDataDto> {
        return listOfNotNull(
            InlineMarkupDataDto(
                5,
                "\uD83D\uDEABОтменить",
                "CANCEL"
            ),
            data?.poi?.name?.let {
                InlineMarkupDataDto(
                    6,
                    "☑\uFE0FЗавершить",
                    "COMPLETE"
                )
            }
        )
    }

}