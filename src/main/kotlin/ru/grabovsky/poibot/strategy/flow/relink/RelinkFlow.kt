package ru.grabovsky.poibot.strategy.flow.relink

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.service.interfaces.I18nService
import ru.grabovsky.poibot.service.interfaces.PlaceLinkService
import ru.grabovsky.poibot.service.interfaces.RelinkResult
import ru.grabovsky.poibot.service.interfaces.SavedPlaceService
import ru.grabovsky.poibot.strategy.flow.addplace.CandidateView
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.core.support.buildMessage
import ru.grabovsky.poibot.strategy.flow.places.CardRef
import ru.grabovsky.poibot.strategy.flow.places.PlaceCardFactory
import ru.grabovsky.poibot.strategy.flow.places.PlaceFormatter
import java.util.*

enum class RelinkStep(override val key: String) : FlowStep {
    PICK("pick"),
}

data class RelinkState(
    var savedPlaceId: Long? = null,
    /** Карточка, из которой открыта привязка: перерисовывается после объединения. */
    var card: CardRef? = null,
)

/** Модель шаблона `relink/pick`. */
data class RelinkView(val candidates: List<CandidateView>)

/**
 * Исправление привязки записи к месту: если при сохранении запись попала в отдельное место, хотя это то же
 * заведение, её можно объединить с найденным рядом. Рейтинг и комментарии станут общими.
 */
@Component
class RelinkFlow(
    private val placeLinkService: PlaceLinkService,
    private val formatter: PlaceFormatter,
    private val cardFactory: PlaceCardFactory,
    private val savedPlaceService: SavedPlaceService,
    private val i18n: I18nService,
) : FlowHandler<RelinkState> {

    override val key: FlowKey = FlowKeys.RELINK
    override val payloadType: Class<RelinkState> = RelinkState::class.java

    override fun start(context: FlowStartContext): FlowResult<RelinkState> =
        FlowResult(RelinkStep.PICK.key, RelinkState(), emptyList())

    override fun onMessage(context: FlowContext<RelinkState>, message: Message): FlowResult<RelinkState>? = null

    override fun onCallback(
        context: FlowContext<RelinkState>,
        callbackQuery: CallbackQuery,
        data: String,
    ): FlowResult<RelinkState>? {
        val (command, argument) = parseCallback(data)
        val state = context.state.payload
        val userId = callbackQuery.from.id
        val locale = context.locale
        val answer = AnswerCallbackAction(callbackQuery.id)
        val messageId = callbackQuery.message?.messageId
        val parts = argument?.split(':').orEmpty()

        val actions: List<FlowAction> = when (command) {
            "OPEN" -> {
                val savedId = parts.getOrNull(0)?.toLongOrNull() ?: return null
                val candidates = placeLinkService.candidatesFor(userId, savedId)
                if (candidates.isEmpty()) {
                    return alert(state, callbackQuery, "alerts.relink.none", locale)
                }
                state.savedPlaceId = savedId
                callbackQuery.message?.messageId?.let { cardId ->
                    state.card = CardRef(
                        messageId = cardId,
                        savedId = savedId,
                        owner = if (parts.getOrNull(1) == PlaceCardFactory.OWNER_NEARBY) PlaceCardFactory.OWNER_NEARBY else PlaceCardFactory.OWNER_PLACES,
                        distance = parts.getOrNull(2)?.toIntOrNull() ?: 0,
                    )
                }
                val buttons = candidates.mapIndexed { index, candidate ->
                    val label = "${candidate.name.take(BUTTON_NAME_LENGTH)} · ${formatter.distance(candidate.distanceMeters, locale)}"
                    FlowInlineButton(label, payload("LINK:$savedId:${candidate.placeId}"), row = index)
                } + FlowInlineButton(i18n.i18n("buttons.common.cancel", locale), payload("CLOSE"), row = FlowInlineButton.LAST_ROW)
                val message = key.buildMessage(
                    step = RelinkStep.PICK,
                    model = RelinkView(candidates.map { CandidateView(it.name, it.address, formatter.distance(it.distanceMeters, locale)) }),
                    inlineButtons = buttons,
                    parseMode = FlowParseMode.HTML,
                )
                listOf(DeleteMessageAction(MAIN_BINDING), SendMessageAction(MAIN_BINDING, message), answer)
            }

            "LINK" -> {
                val savedId = parts.getOrNull(0)?.toLongOrNull() ?: return null
                val placeId = parts.getOrNull(1)?.toLongOrNull() ?: return null
                when (placeLinkService.relink(userId, savedId, placeId)) {
                    RelinkResult.RELINKED -> listOfNotNull(
                        messageId?.let { DeleteMessageIdAction(it) },
                        AnswerCallbackAction(callbackQuery.id, i18n.i18n("alerts.relink.done", locale), showAlert = true),
                    ) + refreshCard(state, userId, locale)

                    RelinkResult.SAME_PLACE, RelinkResult.NOT_FOUND ->
                        return alert(state, callbackQuery, "alerts.place.not_found", locale)
                }
            }

            "CLOSE" -> listOfNotNull(messageId?.let { DeleteMessageIdAction(it) }, answer)

            else -> return null
        }
        return FlowResult(RelinkStep.PICK.key, state, actions)
    }

    private fun refreshCard(state: RelinkState, userId: Long, locale: Locale): List<FlowAction> {
        val ref = state.card ?: return emptyList()
        val place = savedPlaceService.get(userId, ref.savedId) ?: return emptyList()
        return listOf(cardFactory.refreshAction(ref, place, locale))
    }

    private fun alert(state: RelinkState, callbackQuery: CallbackQuery, textKey: String, locale: Locale) =
        FlowResult(
            RelinkStep.PICK.key, state,
            listOf(AnswerCallbackAction(callbackQuery.id, i18n.i18n(textKey, locale), showAlert = true)),
        )

    private fun payload(data: String) = FlowCallbackPayload(key.value, data)

    private companion object {
        const val MAIN_BINDING = "main"
        const val BUTTON_NAME_LENGTH = 30
    }
}
