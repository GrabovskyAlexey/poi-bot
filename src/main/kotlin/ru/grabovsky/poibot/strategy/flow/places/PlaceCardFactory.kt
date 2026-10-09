package ru.grabovsky.poibot.strategy.flow.places

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.service.interfaces.I18nService
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.core.support.buildMessage
import java.util.*

data class PlaceCardView(
    val name: String,
    val address: String?,
    val description: String?,
    val website: String?,
    val distanceText: String?,
)

enum class PlacesStep(override val key: String) : FlowStep {
    LIST("list"),
    CARD("card"),
    CONFIRM_DELETE("confirm_delete"),
}

/**
 * Карточка места. Кнопки «на карте» и «закрыть» адресованы тому flow, который показывает карточку
 * (`owner`), а шаблоны всегда берутся из каталога `places`.
 */
@Component
class PlaceCardFactory(
    private val i18n: I18nService,
    private val formatter: PlaceFormatter,
) {
    fun cardActions(
        owner: FlowKey,
        place: SavedPlace,
        locale: Locale,
        binding: String,
        manage: Boolean,
        distanceMeters: Int? = null,
    ): List<FlowAction> {
        val message = FlowMessage(
            flowKey = FlowKeys.PLACES,
            stepKey = PlacesStep.CARD.key,
            model = PlaceCardView(
                name = place.name,
                address = place.address,
                description = formatter.shorten(place.description, MAX_DESCRIPTION),
                website = place.websiteUrl,
                distanceText = distanceMeters?.let { formatter.distance(it, locale) },
            ),
            inlineButtons = buttons(owner, place, locale, manage),
            parseMode = FlowParseMode.HTML,
        )
        val photo = place.photoFileId
        return listOf(
            if (photo != null) SendPhotoAction(binding, photo, message) else SendMessageAction(binding, message)
        )
    }

    private fun buttons(owner: FlowKey, place: SavedPlace, locale: Locale, manage: Boolean): List<FlowInlineButton> {
        val id = place.id!!
        val result = mutableListOf<FlowInlineButton>()
        var row = 0
        if (place.hasLocation()) {
            result += button(owner, "buttons.places.map", locale, "MAP:$id", row, 0)
        }
        result += button(FlowKeys.ADD_PLACE, "buttons.places.edit", locale, "EDIT:$id", row, 1)
        row++
        if (manage) {
            result += button(owner, "buttons.places.delete", locale, "DELASK:$id", row, 0)
        }
        result += button(owner, "buttons.places.close", locale, "CLOSE", row, 1)
        return result
    }

    private fun button(flow: FlowKey, textKey: String, locale: Locale, data: String, row: Int, col: Int) =
        FlowInlineButton(i18n.i18n(textKey, locale), FlowCallbackPayload(flow.value, data), row, col)

    private companion object {
        const val MAX_DESCRIPTION = 600
    }
}
