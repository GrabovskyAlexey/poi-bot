package ru.grabovsky.poibot.strategy.flow.places

import org.springframework.stereotype.Component
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.service.interfaces.I18nService
import ru.grabovsky.poibot.service.interfaces.PlaceLinkService
import ru.grabovsky.poibot.service.interfaces.ReviewService
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import java.util.*

data class PlaceCardView(
    val name: String,
    val address: String?,
    val description: String?,
    val website: String?,
    val distanceText: String?,
    /** «4.3 (12)»; null, если оценок ещё нет. */
    val rating: String? = null,
)

enum class PlacesStep(override val key: String) : FlowStep {
    LIST("list"),
    CARD("card"),
    CONFIRM_DELETE("confirm_delete"),
}

/**
 * Ссылка на уже показанную карточку: flow оценок и привязки по ней перерисовывают карточку после изменения
 * (оценка, комментарий, смена места), не дожидаясь, пока пользователь откроет её заново.
 */
data class CardRef(
    var messageId: Int = 0,
    var savedId: Long = 0,
    /** [PlaceCardFactory.OWNER_PLACES] или [PlaceCardFactory.OWNER_NEARBY]. */
    var owner: String = PlaceCardFactory.OWNER_PLACES,
    var distance: Int = 0,
)

/**
 * Карточка своей записи. Кнопки «на карте» и «закрыть» адресованы тому flow, который показывает карточку
 * (`owner`), а шаблоны всегда берутся из каталога `places`.
 */
@Component
class PlaceCardFactory(
    private val i18n: I18nService,
    private val formatter: PlaceFormatter,
    private val reviewService: ReviewService,
    private val placeLinkService: PlaceLinkService,
) {
    fun cardActions(
        owner: FlowKey,
        place: SavedPlace,
        locale: Locale,
        binding: String,
        manage: Boolean,
        distanceMeters: Int? = null,
    ): List<FlowAction> {
        val message = cardMessage(owner, place, locale, manage, distanceMeters)
        val photo = place.photoFileId
        return listOf(
            if (photo != null) SendPhotoAction(binding, photo, message) else SendMessageAction(binding, message)
        )
    }

    /** Обновлённое содержимое уже показанной карточки [ref] (после оценки, комментария, смены места). */
    fun refreshAction(ref: CardRef, place: SavedPlace, locale: Locale): FlowAction {
        val owner = if (ref.owner == OWNER_NEARBY) FlowKeys.NEARBY else FlowKeys.PLACES
        val message = cardMessage(owner, place, locale, manage = owner == FlowKeys.PLACES, ref.distance.takeIf { it > 0 })
        return EditCardAction(ref.messageId, message, caption = place.photoFileId != null)
    }

    private fun cardMessage(
        owner: FlowKey,
        place: SavedPlace,
        locale: Locale,
        manage: Boolean,
        distanceMeters: Int?,
    ): FlowMessage = FlowMessage(
        flowKey = FlowKeys.PLACES,
        stepKey = PlacesStep.CARD.key,
        model = PlaceCardView(
            name = place.name,
            address = place.address,
            description = formatter.shorten(place.description, MAX_DESCRIPTION),
            website = place.websiteUrl,
            distanceText = distanceMeters?.let { formatter.distance(it, locale) },
            rating = formatter.rating(reviewService.summary(place.placeId)),
        ),
        inlineButtons = buttons(owner, place, locale, manage, distanceMeters),
        parseMode = FlowParseMode.HTML,
    )

    private fun buttons(
        owner: FlowKey,
        place: SavedPlace,
        locale: Locale,
        manage: Boolean,
        distanceMeters: Int?,
    ): List<FlowInlineButton> {
        val id = place.id!!
        // Контекст карточки для flow оценок/привязки: откуда открыта и расстояние (чтобы не потерять его при перерисовке)
        val context = "${if (owner == FlowKeys.NEARBY) OWNER_NEARBY else OWNER_PLACES}:${distanceMeters ?: 0}"
        val result = mutableListOf<FlowInlineButton>()
        var row = 0
        if (place.hasLocation()) {
            result += button(owner, "buttons.places.map", locale, "MAP:$id", row, 0)
        }
        result += button(FlowKeys.ADD_PLACE, "buttons.places.edit", locale, "EDIT:$id", row, 1)
        row++
        if (manage) {
            result += button(owner, "buttons.places.delete", locale, "DELASK:$id", FlowInlineButton.LAST_ROW, 0)
        }
        result += button(owner, "buttons.places.close", locale, "CLOSE", FlowInlineButton.LAST_ROW, 1)
        result += button(FlowKeys.PUBLISH, "buttons.places.publish", locale, "ONE:$id", row, 0)
        result += button(FlowKeys.SHARED, "buttons.places.share", locale, "OUT:$id", row, 1)
        row++
        result += button(FlowKeys.REVIEWS, "buttons.reviews.rate", locale, "RATE:$id:$context", row, 0)
        val hasComment = reviewService.userComment(place.placeId, place.ownerId) != null
        val commentKey = if (hasComment) "buttons.reviews.comment_edit" else "buttons.reviews.comment"
        result += button(FlowKeys.REVIEWS, commentKey, locale, "ADDC:$id:$context", row, 1)
        row++
        var col = 0
        val comments = reviewService.commentCount(place.placeId)
        if (comments > 0) {
            result += FlowInlineButton(
                i18n.i18n("buttons.reviews.comments", locale, null, comments),
                FlowCallbackPayload(FlowKeys.REVIEWS.value, "COM:${place.placeId}:0:$id:$context"), row, col++,
            )
        }
        if (placeLinkService.candidatesFor(place.ownerId, id).isNotEmpty()) {
            result += button(FlowKeys.RELINK, "buttons.relink.open", locale, "OPEN:$id:$context", row, col)
        }
        return result
    }

    private fun button(flow: FlowKey, textKey: String, locale: Locale, data: String, row: Int, col: Int) =
        FlowInlineButton(i18n.i18n(textKey, locale), FlowCallbackPayload(flow.value, data), row, col)

    companion object {
        const val OWNER_PLACES = "P"
        const val OWNER_NEARBY = "N"
        private const val MAX_DESCRIPTION = 600
    }
}
