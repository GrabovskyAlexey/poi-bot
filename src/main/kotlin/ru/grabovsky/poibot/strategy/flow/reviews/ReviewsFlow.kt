package ru.grabovsky.poibot.strategy.flow.reviews

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.service.interfaces.*
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.core.support.buildMessage
import ru.grabovsky.poibot.strategy.flow.places.CardRef
import ru.grabovsky.poibot.strategy.flow.places.PlaceCardFactory
import ru.grabovsky.poibot.strategy.flow.places.PlaceFormatter
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

enum class ReviewsStep(override val key: String) : FlowStep {
    RATE("rate"),
    PROMPT("prompt"),
    COMMENTS("comments"),
    NOTICE("notice"),
}

/** Запись, к которой пользователь пишет комментарий (ждём следующий текст). */
data class ReviewsState(
    var awaitingSavedId: Long? = null,
    /** Карточка, из которой пользователь пришёл: её нужно перерисовать после изменений. */
    var card: CardRef? = null,
)

/** Модель шаблона `reviews/rate`. */
data class RateView(val name: String, val current: Int?, val rating: String?)

data class CommentLine(val index: Int, val text: String, val date: String, val mine: Boolean)

/** Модель шаблона `reviews/comments` (также для групп). */
data class CommentsView(
    val lines: List<CommentLine>,
    val page: Int,
    val totalPages: Int,
    val total: Long,
    val rating: String?,
    /** false в группах: там комментарии только для чтения. */
    val manage: Boolean = true,
)

/** Модель шаблона `reviews/prompt`: current — прежний комментарий пользователя (при редактировании). */
data class ReviewPromptView(val current: String?)

/** Модель шаблона `reviews/notice`. */
data class NoticeView(val text: String)

/**
 * Оценки и комментарии к месту. Оценивать и писать может только тот, у кого есть запись о месте
 * (`RATE:<savedId>`, `ADDC:<savedId>`); читать комментарии может любой (`COM:<placeId>:<page>`), авторы скрыты.
 */
@Component
class ReviewsFlow(
    private val reviewService: ReviewService,
    private val savedPlaceService: SavedPlaceService,
    private val formatter: PlaceFormatter,
    private val cardFactory: PlaceCardFactory,
    private val i18n: I18nService,
) : FlowHandler<ReviewsState> {

    override val key: FlowKey = FlowKeys.REVIEWS
    override val payloadType: Class<ReviewsState> = ReviewsState::class.java

    /** Вся работа выполняется в onCallback, который сразу следует за запуском; повторов здесь быть не должно. */
    override fun start(context: FlowStartContext): FlowResult<ReviewsState> =
        FlowResult(ReviewsStep.RATE.key, ReviewsState(), emptyList())

    override fun onMessage(context: FlowContext<ReviewsState>, message: Message): FlowResult<ReviewsState>? {
        val state = context.state.payload
        val savedId = state.awaitingSavedId ?: return null
        val text = message.text ?: return null
        val userId = message.from.id
        val locale = context.locale

        val result = reviewService.saveComment(userId, savedId, text)
        val feedbackKey = when (result) {
            AddCommentResult.ADDED -> "notices.comment.added"
            AddCommentResult.UPDATED -> "notices.comment.updated"
            AddCommentResult.TOO_SHORT -> "alerts.reviews.too_short"
            AddCommentResult.TOO_LONG -> "alerts.reviews.too_long"
            AddCommentResult.HIDDEN -> "alerts.reviews.hidden"
            AddCommentResult.NOT_OWNER -> "alerts.place.not_found"
        }
        // При ошибке длины остаёмся в режиме ввода, иначе завершаем
        val keepWaiting = result == AddCommentResult.TOO_SHORT || result == AddCommentResult.TOO_LONG
        val actions = mutableListOf<FlowAction>(DeleteMessageIdAction(message.messageId))
        if (!keepWaiting) {
            state.awaitingSavedId = null
            actions += DeleteMessageAction(PROMPT_BINDING)
        }
        if (result == AddCommentResult.ADDED || result == AddCommentResult.UPDATED) actions += refreshCard(state, userId, locale)
        actions += SendMessageAction(
            null,
            notice(i18n.i18n(feedbackKey, locale, null, ReviewService.MAX_COMMENT_LENGTH)),
        )
        return FlowResult(ReviewsStep.PROMPT.key, state, actions)
    }

    override fun onCallback(
        context: FlowContext<ReviewsState>,
        callbackQuery: CallbackQuery,
        data: String,
    ): FlowResult<ReviewsState>? {
        val (command, argument) = parseCallback(data)
        val state = context.state.payload
        val userId = callbackQuery.from.id
        val locale = context.locale
        val answer = AnswerCallbackAction(callbackQuery.id)
        val cardMessageId = callbackQuery.message?.messageId
        val parts = argument?.split(':').orEmpty()

        val actions: List<FlowAction> = when (command) {
            "RATE" -> {
                val savedId = parts.getOrNull(0)?.toLongOrNull() ?: return null
                rememberCard(state, cardMessageId, savedId, parts.getOrNull(1), parts.getOrNull(2))
                val place = savedPlaceService.get(userId, savedId) ?: return alert(state, callbackQuery, "alerts.place.not_found", locale)
                val current = reviewService.userRating(place.placeId, userId)
                val summary = reviewService.summary(place.placeId)
                listOf(
                    DeleteMessageAction(MAIN_BINDING),
                    SendMessageAction(MAIN_BINDING, rateMessage(savedId, place.name, current, summary, locale)),
                    answer,
                )
            }

            "SET" -> {
                val savedId = parts.getOrNull(0)?.toLongOrNull() ?: return null
                val value = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..5 } ?: return null
                when (reviewService.rate(userId, savedId, value)) {
                    RateResult.OK -> listOfNotNull(
                        cardMessageId?.let { DeleteMessageIdAction(it) },
                        AnswerCallbackAction(callbackQuery.id, i18n.i18n("alerts.reviews.rated", locale, null, value)),
                    ) + refreshCard(state, userId, locale)

                    RateResult.NOT_OWNER -> return alert(state, callbackQuery, "alerts.place.not_found", locale)
                }
            }

            "UNSET" -> {
                val savedId = parts.getOrNull(0)?.toLongOrNull() ?: return null
                reviewService.removeRating(userId, savedId)
                listOfNotNull(
                    cardMessageId?.let { DeleteMessageIdAction(it) },
                    AnswerCallbackAction(callbackQuery.id, i18n.i18n("alerts.reviews.unrated", locale)),
                ) + refreshCard(state, userId, locale)
            }

            "ADDC" -> {
                val savedId = parts.getOrNull(0)?.toLongOrNull() ?: return null
                rememberCard(state, cardMessageId, savedId, parts.getOrNull(1), parts.getOrNull(2))
                val saved = savedPlaceService.get(userId, savedId) ?: return alert(state, callbackQuery, "alerts.place.not_found", locale)
                state.awaitingSavedId = savedId
                val current = reviewService.userComment(saved.placeId, userId)?.text
                listOf(
                    DeleteMessageAction(PROMPT_BINDING),
                    SendMessageAction(PROMPT_BINDING, promptMessage(current, locale)),
                    answer,
                )
            }

            "PCANCEL" -> {
                state.awaitingSavedId = null
                listOf(DeleteMessageAction(PROMPT_BINDING), answer)
            }

            // Первое открытие списка из карточки: карточку не трогаем
            "COM" -> {
                val placeId = parts.getOrNull(0)?.toLongOrNull() ?: return null
                val page = parts.getOrNull(1)?.toIntOrNull() ?: 0
                parts.getOrNull(2)?.toLongOrNull()?.let { rememberCard(state, cardMessageId, it, parts.getOrNull(3), parts.getOrNull(4)) }
                listOf(DeleteMessageAction(COMMENTS_BINDING), SendMessageAction(COMMENTS_BINDING, commentsMessage(placeId, userId, page, locale)), answer)
            }

            // Листание и обновление: заменяем само сообщение со списком
            "CP" -> {
                val placeId = parts.getOrNull(0)?.toLongOrNull() ?: return null
                val page = parts.getOrNull(1)?.toIntOrNull() ?: 0
                refreshComments(cardMessageId, placeId, userId, page, locale) + answer
            }

            "CDEL" -> {
                val commentId = parts.getOrNull(0)?.toLongOrNull() ?: return null
                val placeId = parts.getOrNull(1)?.toLongOrNull() ?: return null
                val page = parts.getOrNull(2)?.toIntOrNull() ?: 0
                reviewService.deleteComment(userId, commentId)
                refreshComments(cardMessageId, placeId, userId, page, locale) +
                        AnswerCallbackAction(callbackQuery.id, i18n.i18n("alerts.reviews.deleted", locale)) +
                        refreshCard(state, userId, locale)
            }

            "CREP" -> {
                val commentId = parts.getOrNull(0)?.toLongOrNull() ?: return null
                val placeId = parts.getOrNull(1)?.toLongOrNull() ?: return null
                val page = parts.getOrNull(2)?.toIntOrNull() ?: 0
                val reportKey = when (reviewService.report(userId, commentId)) {
                    ReportResult.REPORTED -> "alerts.reviews.reported"
                    ReportResult.ALREADY_REPORTED -> "alerts.reviews.already_reported"
                    ReportResult.OWN_COMMENT -> "alerts.reviews.own_comment"
                    ReportResult.NOT_FOUND -> "alerts.reviews.gone"
                }
                refreshComments(cardMessageId, placeId, userId, page, locale) +
                        AnswerCallbackAction(callbackQuery.id, i18n.i18n(reportKey, locale), showAlert = true) +
                        refreshCard(state, userId, locale)
            }

            "CLOSE" -> listOfNotNull(cardMessageId?.let { DeleteMessageIdAction(it) }, answer)

            else -> return null
        }
        return FlowResult(ReviewsStep.COMMENTS.key, state, actions)
    }

    // --- сообщения ---------------------------------------------------------------------------

    private fun rememberCard(state: ReviewsState, cardMessageId: Int?, savedId: Long, owner: String?, distance: String?) {
        cardMessageId ?: return
        state.card = CardRef(
            messageId = cardMessageId,
            savedId = savedId,
            owner = if (owner == PlaceCardFactory.OWNER_NEARBY) PlaceCardFactory.OWNER_NEARBY else PlaceCardFactory.OWNER_PLACES,
            distance = distance?.toIntOrNull() ?: 0,
        )
    }

    /** Перерисовывает карточку, из которой пришёл пользователь (рейтинг, число комментариев). */
    private fun refreshCard(state: ReviewsState, userId: Long, locale: Locale): List<FlowAction> {
        val ref = state.card ?: return emptyList()
        val place = savedPlaceService.get(userId, ref.savedId) ?: return emptyList()
        return listOf(cardFactory.refreshAction(ref, place, locale))
    }

    private fun alert(state: ReviewsState, callbackQuery: CallbackQuery, textKey: String, locale: Locale) =
        FlowResult(
            ReviewsStep.COMMENTS.key, state,
            listOf(AnswerCallbackAction(callbackQuery.id, i18n.i18n(textKey, locale), showAlert = true)),
        )

    private fun refreshComments(
        messageId: Int?,
        placeId: Long,
        userId: Long,
        page: Int,
        locale: Locale,
    ): List<FlowAction> {
        val removeOld = listOfNotNull(messageId?.let { DeleteMessageIdAction(it) })
        // Комментариев не осталось — пустой список не показываем
        if (reviewService.commentCount(placeId) == 0) return removeOld
        return removeOld + SendMessageAction(COMMENTS_BINDING, commentsMessage(placeId, userId, page, locale))
    }

    private fun rateMessage(savedId: Long, name: String, current: Int?, summary: RatingSummary, locale: Locale): FlowMessage {
        val stars = (1..5).map { value ->
            val mark = if (value == current) "✅ " else ""
            FlowInlineButton("$mark$value ⭐", payload("SET:$savedId:$value"), row = 0, col = value - 1)
        }
        val extra = buildList {
            if (current != null) add(FlowInlineButton(i18n.i18n("buttons.reviews.unrate", locale), payload("UNSET:$savedId"), 1, 0))
            add(FlowInlineButton(i18n.i18n("buttons.places.close", locale), payload("CLOSE"), FlowInlineButton.LAST_ROW, 0))
        }
        return key.buildMessage(
            step = ReviewsStep.RATE,
            model = RateView(name, current, formatter.rating(summary)),
            inlineButtons = stars + extra,
            parseMode = FlowParseMode.HTML,
        )
    }

    private fun promptMessage(current: String?, locale: Locale): FlowMessage =
        key.buildMessage(
            step = ReviewsStep.PROMPT,
            model = ReviewPromptView(current),
            inlineButtons = listOf(FlowInlineButton(i18n.i18n("buttons.common.cancel", locale), payload("PCANCEL"))),
            parseMode = FlowParseMode.HTML,
        )

    private fun commentsMessage(placeId: Long, userId: Long, page: Int, locale: Locale): FlowMessage {
        val result = reviewService.comments(placeId, userId, page, PAGE_SIZE)
        val lines = result.items.mapIndexed { index, item ->
            CommentLine(
                index = result.page * PAGE_SIZE + index + 1,
                text = item.text,
                date = item.createdAt?.atOffset(ZoneOffset.UTC)?.format(DATE_FORMAT).orEmpty(),
                mine = item.mine,
            )
        }
        val buttons = mutableListOf<FlowInlineButton>()
        result.items.forEachIndexed { index, item ->
            val label = if (item.mine) "🗑 ${lines[index].index}" else "🚩 ${lines[index].index}"
            val command = if (item.mine) "CDEL" else "CREP"
            buttons += FlowInlineButton(label, payload("$command:${item.id}:$placeId:${result.page}"), row = 0, col = index)
        }
        var row = 1
        if (result.totalPages > 1) {
            if (result.page > 0) buttons += FlowInlineButton("◀", payload("CP:$placeId:${result.page - 1}"), row, 0)
            if (result.page < result.totalPages - 1) buttons += FlowInlineButton("▶", payload("CP:$placeId:${result.page + 1}"), row, 1)
            row++
        }
        buttons += FlowInlineButton(i18n.i18n("buttons.places.close", locale), payload("CLOSE"), FlowInlineButton.LAST_ROW, 0)
        return key.buildMessage(
            step = ReviewsStep.COMMENTS,
            model = CommentsView(lines, result.page + 1, result.totalPages, result.total, formatter.rating(reviewService.summary(placeId))),
            inlineButtons = buttons,
            parseMode = FlowParseMode.HTML,
        )
    }

    private fun notice(text: String): FlowMessage =
        key.buildMessage(
            step = ReviewsStep.NOTICE,
            model = NoticeView(text),
            parseMode = FlowParseMode.HTML,
            autoDeleteAfterSeconds = NOTICE_VISIBLE_SECONDS,
        )

    private fun payload(data: String) = FlowCallbackPayload(key.value, data)

    private companion object {
        const val MAIN_BINDING = "main"
        const val PROMPT_BINDING = "prompt"
        const val COMMENTS_BINDING = "comments"
        const val PAGE_SIZE = 5
        const val NOTICE_VISIBLE_SECONDS = 6
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }
}
