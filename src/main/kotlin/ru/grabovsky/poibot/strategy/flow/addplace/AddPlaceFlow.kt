package ru.grabovsky.poibot.strategy.flow.addplace

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.grabovsky.poibot.service.interfaces.*
import ru.grabovsky.poibot.strategy.flow.core.engine.*
import ru.grabovsky.poibot.strategy.flow.core.support.*
import ru.grabovsky.poibot.strategy.flow.places.PlaceFormatter
import ru.grabovsky.poibot.util.UrlUtils
import java.util.*

/**
 * Форма добавления/редактирования точки. Поля заполняются в любом порядке: либо кнопками,
 * либо просто присланным сообщением нужного типа (локация, фото, venue, ссылка, текст).
 */
@Component
class AddPlaceFlow(
    private val savedPlaceService: SavedPlaceService,
    private val placeMatchingService: PlaceMatchingService,
    private val formatter: PlaceFormatter,
    private val i18n: I18nService,
) : FlowHandler<AddPlaceState> {

    override val key: FlowKey = FlowKeys.ADD_PLACE
    override val payloadType: Class<AddPlaceState> = AddPlaceState::class.java

    override fun start(context: FlowStartContext): FlowResult<AddPlaceState> {
        val state = AddPlaceState()
        editIdFrom(context.args)?.let { id ->
            savedPlaceService.get(context.user.id, id)?.let(state::loadFrom)
        }
        return FlowResult(
            stepKey = AddPlaceStep.FORM.key,
            payload = state,
            actions = listOf(SendMessageAction(FORM_BINDING, formMessage(state, context.locale))),
        )
    }

    override fun onCallback(
        context: FlowContext<AddPlaceState>,
        callbackQuery: CallbackQuery,
        data: String,
    ): FlowResult<AddPlaceState>? {
        val (command, argument) = parseCallback(data)
        return when (command) {
            "EDIT" -> onEdit(context, callbackQuery, argument?.toLongOrNull())
            "FIELD" -> PlaceField.fromCode(argument)?.let { onField(context, callbackQuery, it) }
            "PROMPT" -> onPrompt(context, callbackQuery, argument)
            "PUT" -> PlaceField.fromCode(argument)?.let { onPut(context, callbackQuery, it) }
            "SAVE" -> onSave(context, callbackQuery)
            "LINK" -> argument?.toLongOrNull()?.let { save(context, callbackQuery, it) }
            "NEW" -> save(context, callbackQuery, null)
            "NO" -> onRejectCandidate(context, callbackQuery)
            "BACK" -> onBack(context, callbackQuery)
            "CANCEL" -> onCancel(context, callbackQuery)
            else -> null
        }
    }

    override fun onMessage(context: FlowContext<AddPlaceState>, message: Message): FlowResult<AddPlaceState>? {
        val state = context.state.payload
        val locale = context.locale
        val field = PlaceField.fromCode(state.awaitingField)
        val outcome = if (field != null) applyToField(state, field, message) else applySmart(state, message)

        return when (outcome) {
            is Outcome.Applied -> {
                if (field != null) {
                    context.finalizePrompt(
                        targetStep = AddPlaceStep.FORM,
                        userMessageId = message.messageId,
                        updateState = { awaitingField = null },
                    ) { this += refreshForm(context, locale) }
                } else {
                    FlowResult(
                        stepKey = AddPlaceStep.FORM.key,
                        payload = state,
                        actions = listOf(DeleteMessageIdAction(message.messageId)) + refreshForm(context, locale),
                    )
                }
            }

            is Outcome.Invalid -> {
                val promptField = field ?: PlaceField.NAME
                val cleanup = state.cleanupPromptMessages()
                context.retryPrompt(
                    targetStep = AddPlaceStep.PROMPT,
                    bindingPrefix = PROMPT_BINDING,
                    userMessageId = message.messageId,
                    updateState = { awaitingField = promptField.code },
                    appendActions = { addAll(cleanup) },
                ) { promptMessage(promptField, state, locale, error = i18n.i18n(outcome.errorKey, locale, null, promptField.maxLength)) }
            }

            is Outcome.Ambiguous -> {
                val cleanup = state.cleanupPromptMessages()
                state.pendingText = outcome.text
                state.pendingMessageId = message.messageId
                val binding = PromptSupport.nextBinding(PROMPT_BINDING)
                state.promptBindings.add(binding)
                FlowResult(
                    stepKey = AddPlaceStep.FIELD_CHOICE.key,
                    payload = state,
                    actions = cleanup + SendMessageAction(binding, fieldChoiceMessage(outcome.text, locale)),
                )
            }

            Outcome.Ignored -> null
        }
    }

    // --- callbacks ---------------------------------------------------------------------------

    private fun onEdit(
        context: FlowContext<AddPlaceState>,
        callbackQuery: CallbackQuery,
        id: Long?,
    ): FlowResult<AddPlaceState>? {
        id ?: return null
        val state = context.state.payload
        if (state.editingId == id) {
            return result(state, AddPlaceStep.FORM, listOf(AnswerCallbackAction(callbackQuery.id)))
        }
        val place = savedPlaceService.get(callbackQuery.from.id, id)
            ?: return result(state, AddPlaceStep.FORM, listOf(alert(callbackQuery, "alerts.place.not_found", context.locale)))
        val fresh = AddPlaceState().also { it.loadFrom(place) }
        val actions = state.cleanupPromptMessages() + refreshForm(context, context.locale, fresh) +
                AnswerCallbackAction(callbackQuery.id)
        return result(fresh, AddPlaceStep.FORM, actions)
    }

    private fun onField(
        context: FlowContext<AddPlaceState>,
        callbackQuery: CallbackQuery,
        field: PlaceField,
    ): FlowResult<AddPlaceState> {
        val state = context.state.payload
        val cleanup = state.cleanupPromptMessages()
        return context.startPrompt(
            targetStep = AddPlaceStep.PROMPT,
            bindingPrefix = PROMPT_BINDING,
            callbackQuery = callbackQuery,
            updateState = { awaitingField = field.code },
            appendActions = { addAll(cleanup) },
        ) { promptMessage(field, state, context.locale) }
    }

    private fun onPrompt(
        context: FlowContext<AddPlaceState>,
        callbackQuery: CallbackQuery,
        argument: String?,
    ): FlowResult<AddPlaceState>? {
        val state = context.state.payload
        return when (argument) {
            "CANCEL" -> context.cancelPrompt(
                targetStep = AddPlaceStep.FORM,
                callbackQuery = callbackQuery,
                updateState = {
                    awaitingField = null
                    pendingText = null
                    pendingMessageId = null
                },
            )

            "CLEAR" -> {
                val field = PlaceField.fromCode(state.awaitingField) ?: return null
                context.cancelPrompt(
                    targetStep = AddPlaceStep.FORM,
                    callbackQuery = callbackQuery,
                    updateState = {
                        clear(field)
                        awaitingField = null
                    },
                    appendActions = { this += refreshForm(context, context.locale) },
                )
            }

            else -> null
        }
    }

    private fun onPut(
        context: FlowContext<AddPlaceState>,
        callbackQuery: CallbackQuery,
        field: PlaceField,
    ): FlowResult<AddPlaceState>? {
        val state = context.state.payload
        val text = state.pendingText ?: return null
        val messageToDelete = state.pendingMessageId
        if (text.length > field.maxLength) {
            return result(
                state, AddPlaceStep.FIELD_CHOICE,
                listOf(alert(callbackQuery, "alerts.add.too_long", context.locale, field.maxLength)),
            )
        }
        return context.cancelPrompt(
            targetStep = AddPlaceStep.FORM,
            callbackQuery = callbackQuery,
            updateState = {
                when (field) {
                    PlaceField.NAME -> name = text
                    PlaceField.ADDRESS -> address = text
                    PlaceField.DESCRIPTION -> description = text
                    else -> Unit
                }
                pendingText = null
                pendingMessageId = null
            },
            appendActions = {
                messageToDelete?.let { add(DeleteMessageIdAction(it)) }
                this += refreshForm(context, context.locale)
            },
        )
    }

    private fun onSave(context: FlowContext<AddPlaceState>, callbackQuery: CallbackQuery): FlowResult<AddPlaceState> {
        val state = context.state.payload
        val locale = context.locale
        if (state.name.isNullOrBlank()) {
            return result(state, AddPlaceStep.FORM, listOf(alert(callbackQuery, "alerts.add.name_required", locale)))
        }
        val needResolve = state.hasLocation() && (state.editingId == null || state.geoChanged())
        if (needResolve) {
            val candidates = placeMatchingService.findCandidates(state.lat!!, state.lon!!, state.name!!)
            if (candidates.isNotEmpty()) {
                state.candidates = candidates.map {
                    CandidateDto(it.placeId, it.name, it.address, it.distanceMeters, it.similarity)
                }.toMutableList()
                state.resolveStrong = candidates.first().similarity >= PlaceMatchingService.STRONG_SIMILARITY
                val actions = state.cleanupPromptMessages() +
                        EditMessageAction(FORM_BINDING, resolveMessage(state, locale)) +
                        AnswerCallbackAction(callbackQuery.id)
                state.awaitingField = null
                return result(state, AddPlaceStep.RESOLVE, actions)
            }
        }
        return save(context, callbackQuery, null)
    }

    private fun onRejectCandidate(
        context: FlowContext<AddPlaceState>,
        callbackQuery: CallbackQuery,
    ): FlowResult<AddPlaceState> {
        val state = context.state.payload
        if (state.resolveStrong && state.candidates.size > 1) {
            state.candidates.removeAt(0)
            state.resolveStrong = false
            return result(
                state, AddPlaceStep.RESOLVE,
                listOf(EditMessageAction(FORM_BINDING, resolveMessage(state, context.locale)), AnswerCallbackAction(callbackQuery.id)),
            )
        }
        return save(context, callbackQuery, null)
    }

    private fun onBack(context: FlowContext<AddPlaceState>, callbackQuery: CallbackQuery): FlowResult<AddPlaceState> {
        val state = context.state.payload
        state.candidates.clear()
        state.resolveStrong = false
        return result(
            state, AddPlaceStep.FORM,
            listOf(EditMessageAction(FORM_BINDING, formMessage(state, context.locale)), AnswerCallbackAction(callbackQuery.id)),
        )
    }

    private fun onCancel(context: FlowContext<AddPlaceState>, callbackQuery: CallbackQuery): FlowResult<AddPlaceState> {
        val state = context.state.payload
        val actions = state.cleanupPromptMessages() +
                DeleteMessageAction(FORM_BINDING) +
                AnswerCallbackAction(callbackQuery.id)
        return FlowResult(AddPlaceStep.FORM.key, state, actions, completed = true)
    }

    private fun save(
        context: FlowContext<AddPlaceState>,
        callbackQuery: CallbackQuery,
        linkPlaceId: Long?,
    ): FlowResult<AddPlaceState> {
        val state = context.state.payload
        val locale = context.locale
        val ownerId = callbackQuery.from.id
        val draft = SavedPlaceDraft(
            name = state.name!!.trim(),
            address = state.address,
            description = state.description,
            websiteUrl = state.websiteUrl,
            photoFileId = state.photoFileId,
            photoFileUniqueId = state.photoFileUniqueId,
            lat = state.lat,
            lon = state.lon,
        )
        val editing = state.editingId != null
        val saved = try {
            if (editing) savedPlaceService.update(ownerId, state.editingId!!, draft, linkPlaceId)
            else savedPlaceService.create(ownerId, draft, linkPlaceId)
        } catch (error: PlaceLimitExceededException) {
            return result(
                state, AddPlaceStep.FORM,
                listOf(alert(callbackQuery, "alerts.add.limit", locale, error.limit)),
            )
        }
        if (saved == null) {
            logger.warn { "Place ${state.editingId} not found for owner $ownerId while saving" }
            return result(state, AddPlaceStep.FORM, listOf(alert(callbackQuery, "alerts.place.not_found", locale)))
        }
        val actions = state.cleanupPromptMessages() +
                EditMessageAction(FORM_BINDING, savedMessage(saved.name, editing)) +
                AnswerCallbackAction(callbackQuery.id)
        return FlowResult(AddPlaceStep.SAVED.key, state, actions, completed = true)
    }

    // --- обработка сообщений -----------------------------------------------------------------

    private sealed interface Outcome {
        data object Applied : Outcome
        data class Invalid(val errorKey: String) : Outcome
        data class Ambiguous(val text: String) : Outcome
        data object Ignored : Outcome
    }

    private fun applyToField(state: AddPlaceState, field: PlaceField, message: Message): Outcome =
        when (field) {
            PlaceField.NAME, PlaceField.ADDRESS, PlaceField.DESCRIPTION -> {
                val text = message.text?.trim()
                when {
                    text.isNullOrEmpty() -> Outcome.Invalid("alerts.add.text_expected")
                    text.length > field.maxLength -> Outcome.Invalid("alerts.add.too_long")
                    else -> {
                        setText(state, field, text)
                        Outcome.Applied
                    }
                }
            }

            PlaceField.WEBSITE -> {
                val url = message.text?.let(UrlUtils::normalize)
                if (url == null) {
                    Outcome.Invalid("alerts.add.url_invalid")
                } else {
                    state.websiteUrl = url
                    Outcome.Applied
                }
            }

            PlaceField.PHOTO ->
                if (applyPhoto(state, message)) Outcome.Applied else Outcome.Invalid("alerts.add.photo_expected")

            PlaceField.LOCATION ->
                if (applyLocation(state, message) || applyVenue(state, message)) Outcome.Applied
                else Outcome.Invalid("alerts.add.location_expected")
        }

    private fun applySmart(state: AddPlaceState, message: Message): Outcome {
        if (applyVenue(state, message) || applyLocation(state, message) || applyPhoto(state, message)) {
            return Outcome.Applied
        }
        val text = message.text?.trim()?.takeIf { it.isNotEmpty() } ?: return Outcome.Ignored
        if (UrlUtils.looksLikeUrl(text)) {
            UrlUtils.normalize(text)?.let {
                state.websiteUrl = it
                return Outcome.Applied
            }
        }
        if (text.length > PlaceField.NAME.maxLength && state.name.isNullOrBlank()) {
            return Outcome.Invalid("alerts.add.too_long")
        }
        if (state.name.isNullOrBlank()) {
            state.name = text
            return Outcome.Applied
        }
        return Outcome.Ambiguous(text)
    }

    private fun setText(state: AddPlaceState, field: PlaceField, text: String) {
        when (field) {
            PlaceField.NAME -> state.name = text
            PlaceField.ADDRESS -> state.address = text
            PlaceField.DESCRIPTION -> state.description = text
            else -> Unit
        }
    }

    private fun applyLocation(state: AddPlaceState, message: Message): Boolean {
        if (!message.hasLocation()) return false
        state.lat = message.location.latitude
        state.lon = message.location.longitude
        return true
    }

    /** Venue даёт название, адрес и координаты сразу; пустые поля заполняем, геопозицию всегда обновляем. */
    private fun applyVenue(state: AddPlaceState, message: Message): Boolean {
        if (!(message.venue != null)) return false
        val venue = message.venue
        if (state.name.isNullOrBlank()) state.name = venue.title?.take(PlaceField.NAME.maxLength)
        if (state.address.isNullOrBlank()) state.address = venue.address?.take(PlaceField.ADDRESS.maxLength)
        state.lat = venue.location.latitude
        state.lon = venue.location.longitude
        return true
    }

    private fun applyPhoto(state: AddPlaceState, message: Message): Boolean {
        if (!message.hasPhoto()) return false
        val photo = message.photo.maxByOrNull { it.fileSize ?: 0 } ?: message.photo.last()
        state.photoFileId = photo.fileId
        state.photoFileUniqueId = photo.fileUniqueId
        return true
    }

    // --- сообщения ---------------------------------------------------------------------------

    private fun result(state: AddPlaceState, step: AddPlaceStep, actions: List<FlowAction>) =
        FlowResult(step.key, state, actions)

    private fun alert(callbackQuery: CallbackQuery, textKey: String, locale: Locale, vararg args: Any?) =
        AnswerCallbackAction(callbackQuery.id, i18n.i18n(textKey, locale, null, *args), showAlert = true)

    /** Обновляет сообщение формы (или отправляет заново, если оно потеряно). */
    private fun refreshForm(
        context: FlowContext<AddPlaceState>,
        locale: Locale,
        state: AddPlaceState = context.state.payload,
    ): List<FlowAction> {
        val message = formMessage(state, locale)
        return if (context.state.messageBindings.containsKey(FORM_BINDING)) {
            listOf(EditMessageAction(FORM_BINDING, message))
        } else {
            listOf(SendMessageAction(FORM_BINDING, message))
        }
    }

    private fun formMessage(state: AddPlaceState, locale: Locale): FlowMessage {
        val view = AddFormView(
            editing = state.editingId != null,
            name = state.name,
            address = state.address,
            hasLocation = state.hasLocation(),
            hasPhoto = state.photoFileId != null,
            website = state.websiteUrl,
            description = formatter.shorten(state.description, FORM_DESCRIPTION_PREVIEW),
        )
        fun field(field: PlaceField, row: Int, col: Int) =
            button("buttons.add.field.${field.code}", locale, "FIELD:${field.code}", row, col)
        return key.buildMessage(
            step = AddPlaceStep.FORM,
            model = view,
            inlineButtons = listOf(
                field(PlaceField.NAME, 0, 0), field(PlaceField.ADDRESS, 0, 1),
                field(PlaceField.LOCATION, 1, 0), field(PlaceField.PHOTO, 1, 1),
                field(PlaceField.WEBSITE, 2, 0), field(PlaceField.DESCRIPTION, 2, 1),
                button("buttons.add.save", locale, "SAVE", 3, 0),
                button("buttons.common.cancel", locale, "CANCEL", 3, 1),
            ),
            parseMode = FlowParseMode.HTML,
        )
    }

    private fun promptMessage(
        field: PlaceField,
        state: AddPlaceState,
        locale: Locale,
        error: String? = null,
    ): FlowMessage {
        val hasValue = state.get(field) != null && field != PlaceField.NAME
        val buttons = mutableListOf(key.cancelPromptButton(i18n.i18n("buttons.common.cancel", locale)))
        if (hasValue) {
            buttons += button("buttons.add.clear", locale, "PROMPT:CLEAR", 0, 1)
        }
        return key.buildMessage(
            step = AddPlaceStep.PROMPT,
            model = PromptView(field.code, hasValue, error),
            inlineButtons = buttons,
            parseMode = FlowParseMode.HTML,
        )
    }

    private fun fieldChoiceMessage(text: String, locale: Locale): FlowMessage =
        key.buildMessage(
            step = AddPlaceStep.FIELD_CHOICE,
            model = FieldChoiceView(formatter.shorten(text, CHOICE_PREVIEW) ?: ""),
            inlineButtons = listOf(
                button("buttons.add.field.name", locale, "PUT:name", 0, 0),
                button("buttons.add.field.address", locale, "PUT:address", 0, 1),
                button("buttons.add.field.description", locale, "PUT:description", 1, 0),
                key.cancelPromptButton(i18n.i18n("buttons.common.cancel", locale)).copy(row = 1, col = 1),
            ),
            parseMode = FlowParseMode.HTML,
        )

    private fun resolveMessage(state: AddPlaceState, locale: Locale): FlowMessage {
        val candidates = state.candidates.map {
            CandidateView(it.name, it.address, formatter.distance(it.distance, locale))
        }
        val buttons = mutableListOf<FlowInlineButton>()
        if (state.resolveStrong) {
            val top = state.candidates.first()
            buttons += button("buttons.resolve.yes", locale, "LINK:${top.placeId}", 0, 0)
            buttons += button("buttons.resolve.no", locale, "NO", 0, 1)
        } else {
            state.candidates.forEachIndexed { index, candidate ->
                val label = "${candidate.name.take(RESOLVE_LABEL_NAME)} · ${formatter.distance(candidate.distance, locale)}"
                buttons += FlowInlineButton(label, FlowCallbackPayload(key.value, "LINK:${candidate.placeId}"), index, 0)
            }
            buttons += button("buttons.resolve.other", locale, "NEW", state.candidates.size, 0)
        }
        buttons += button("buttons.resolve.back", locale, "BACK", state.candidates.size + 1, 0)
        return key.buildMessage(
            step = AddPlaceStep.RESOLVE,
            model = ResolveView(state.resolveStrong, if (state.resolveStrong) candidates.take(1) else candidates),
            inlineButtons = buttons,
            parseMode = FlowParseMode.HTML,
        )
    }

    private fun savedMessage(name: String, editing: Boolean): FlowMessage =
        key.buildMessage(
            step = AddPlaceStep.SAVED,
            model = SavedView(name, editing),
            parseMode = FlowParseMode.HTML,
            autoDeleteAfterSeconds = SAVED_VISIBLE_SECONDS,
        )

    private fun button(textKey: String, locale: Locale, data: String, row: Int, col: Int) =
        FlowInlineButton(i18n.i18n(textKey, locale), FlowCallbackPayload(key.value, data), row, col)

    private fun editIdFrom(args: String?): Long? =
        args?.takeIf { it.startsWith("EDIT:") }?.removePrefix("EDIT:")?.toLongOrNull()

    private companion object {
        val logger = KotlinLogging.logger {}
        const val FORM_BINDING = "form"
        const val PROMPT_BINDING = "prompt"
        const val FORM_DESCRIPTION_PREVIEW = 200
        const val CHOICE_PREVIEW = 100
        const val RESOLVE_LABEL_NAME = 30
        const val SAVED_VISIBLE_SECONDS = 8
    }
}
