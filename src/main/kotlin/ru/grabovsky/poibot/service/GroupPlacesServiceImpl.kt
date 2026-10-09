package ru.grabovsky.poibot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.send.SendVenue
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ForceReplyKeyboard
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.grabovsky.poibot.config.BotConfig
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.geo.NearbyResult
import ru.grabovsky.poibot.geo.SearchRadius
import ru.grabovsky.poibot.service.interfaces.*
import ru.grabovsky.poibot.strategy.flow.nearby.HintView
import ru.grabovsky.poibot.strategy.flow.nearby.NearbyView
import ru.grabovsky.poibot.strategy.flow.nearby.PointView
import ru.grabovsky.poibot.strategy.flow.places.ListItemView
import ru.grabovsky.poibot.strategy.flow.places.PlaceCardView
import ru.grabovsky.poibot.strategy.flow.places.PlaceFormatter
import ru.grabovsky.poibot.strategy.flow.places.PlacesListView
import ru.grabovsky.poibot.util.LocaleUtils
import java.util.*

@Service
class GroupPlacesServiceImpl(
    private val telegramClient: TelegramClient,
    private val botConfig: BotConfig,
    private val userService: UserService,
    private val chatService: ChatService,
    private val publishService: PublishService,
    private val nearbySearchService: NearbySearchService,
    private val messageGenerateService: MessageGenerateService,
    private val formatter: PlaceFormatter,
    private val i18n: I18nService,
) : GroupPlacesService {

    private data class ListData(val view: PlacesListView, val items: List<SavedPlace>)

    override fun showPlaces(chat: Chat, user: User, replyToMessageId: Int?) {
        registerMember(chat, user)
        val locale = localeOf(user)
        val data = listData(chat.id, 0)
        val message = SendMessage.builder()
            .chatId(chat.id)
            .text(render("group/list", data.view, locale))
            .parseMode(HTML)
            .replyMarkup(listMarkup(data))
            .build()
        replyToMessageId?.let { message.replyToMessageId = it }
        run("send list") { telegramClient.execute(message) }
    }

    override fun askNearbyLocation(chat: Chat, user: User, replyToMessageId: Int?) {
        registerMember(chat, user)
        val locale = localeOf(user)
        val message = SendMessage.builder()
            .chatId(chat.id)
            .text(render("group/ask_location", null, locale))
            .parseMode(HTML)
            .replyMarkup(ForceReplyKeyboard.builder().forceReply(true).selective(true).build())
            .build()
        replyToMessageId?.let { message.replyToMessageId = it }
        run("ask location") { telegramClient.execute(message) }
    }

    override fun showAddHint(chat: Chat, user: User, replyToMessageId: Int?) {
        registerMember(chat, user)
        val locale = localeOf(user)
        val button = InlineKeyboardButton(i18n.i18n("buttons.group.open_private", locale)).apply {
            url = "https://t.me/${botConfig.name}"
        }
        val message = SendMessage.builder()
            .chatId(chat.id)
            .text(render("group/add_hint", null, locale))
            .parseMode(HTML)
            .replyMarkup(InlineKeyboardMarkup(listOf(InlineKeyboardRow(button))))
            .build()
        replyToMessageId?.let { message.replyToMessageId = it }
        run("add hint") { telegramClient.execute(message) }
    }

    override fun handleMessage(message: Message): Boolean {
        val repliedTo = message.replyToMessage?.from ?: return false
        if (!repliedTo.isBot || !repliedTo.userName.equals(botConfig.name, ignoreCase = true)) return false
        val location = when {
            message.hasLocation() -> message.location
            message.venue != null -> message.venue.location
            else -> return false
        }
        val user = message.from ?: return false
        registerMember(message.chat, user)
        val locale = localeOf(user)
        val result = nearbySearchService.searchInChat(
            message.chatId, location.latitude, location.longitude, userRadius(user.id),
        )
        val reply = SendMessage.builder()
            .chatId(message.chatId)
            .text(render("nearby/result", nearbyView(result, locale), locale))
            .parseMode(HTML)
            .replyMarkup(nearbyMarkup(result, location.latitude, location.longitude, locale))
            .build()
        reply.replyToMessageId = message.messageId
        run("send nearby") { telegramClient.execute(reply) }
        return true
    }

    override fun onCallback(callbackQuery: CallbackQuery, data: String) {
        val message = callbackQuery.message as? Message ?: return answer(callbackQuery, null)
        val chat = message.chat
        val user = callbackQuery.from
        registerMember(chat, user)
        val locale = localeOf(user)
        val parts = data.split(':')
        when (parts[0]) {
            "P" -> {
                val listData = listData(chat.id, parts.getOrNull(1)?.toIntOrNull() ?: 0)
                edit(chat.id, message.messageId, render("group/list", listData.view, locale), listMarkup(listData))
                answer(callbackQuery, null)
            }

            "O" -> openCard(callbackQuery, message, parts.getOrNull(1)?.toLongOrNull(), locale)
            "M" -> showOnMap(callbackQuery, message, parts.getOrNull(1)?.toLongOrNull())
            "X" -> remove(callbackQuery, message, parts.getOrNull(1)?.toLongOrNull(), locale)
            "C" -> {
                deleteMessage(chat.id, message.messageId)
                answer(callbackQuery, null)
            }

            "R" -> changeRadius(callbackQuery, message, parts, locale)
            else -> answer(callbackQuery, null)
        }
    }

    // --- действия ----------------------------------------------------------------------------

    private fun openCard(callbackQuery: CallbackQuery, message: Message, placeId: Long?, locale: Locale) {
        val place = placeId?.let { publishService.getPublished(message.chatId, it) }
            ?: return answer(callbackQuery, i18n.i18n("alerts.place.not_found", locale), alert = true)
        val card = render(
            "places/card",
            PlaceCardView(
                place.name, place.address, formatter.shorten(place.description, MAX_DESCRIPTION), place.websiteUrl, null,
            ),
            locale,
        )
        val mapAndClose = mutableListOf<InlineKeyboardButton>()
        if (place.hasLocation()) mapAndClose += callbackButton(i18n.i18n("buttons.places.map", locale), "M:${place.id}")
        mapAndClose += callbackButton(i18n.i18n("buttons.places.close", locale), "C")
        val markup = InlineKeyboardMarkup(
            listOf(
                InlineKeyboardRow(mapAndClose),
                InlineKeyboardRow(callbackButton(i18n.i18n("buttons.group.remove", locale), "X:${place.id}")),
            )
        )
        val photo = place.photoFileId
        if (photo != null) {
            val send = SendPhoto.builder().chatId(message.chatId).photo(InputFile(photo))
                .caption(card.take(MAX_CAPTION)).parseMode(HTML).replyMarkup(markup).build()
            send.replyToMessageId = message.messageId
            run("send card photo") { telegramClient.execute(send) }
        } else {
            val send = SendMessage.builder().chatId(message.chatId).text(card).parseMode(HTML)
                .replyMarkup(markup).build()
            send.replyToMessageId = message.messageId
            run("send card") { telegramClient.execute(send) }
        }
        answer(callbackQuery, null)
    }

    private fun showOnMap(callbackQuery: CallbackQuery, message: Message, placeId: Long?) {
        val place = placeId?.let { publishService.getPublished(message.chatId, it) }
        val lat = place?.lat
        val lon = place?.lon
        if (place != null && lat != null && lon != null) {
            val venue = SendVenue.builder().chatId(message.chatId).latitude(lat).longitude(lon)
                .title(place.name).address(place.address ?: "").build()
            venue.replyToMessageId = message.messageId
            run("send venue") { telegramClient.execute(venue) }
        }
        answer(callbackQuery, null)
    }

    private fun remove(callbackQuery: CallbackQuery, message: Message, placeId: Long?, locale: Locale) {
        val removed = placeId != null &&
                publishService.unpublishAsModerator(callbackQuery.from.id, message.chatId, placeId)
        if (removed) {
            deleteMessage(message.chatId, message.messageId)
            answer(callbackQuery, i18n.i18n("alerts.group.removed", locale))
        } else {
            answer(callbackQuery, i18n.i18n("alerts.group.not_allowed", locale), alert = true)
        }
    }

    private fun changeRadius(callbackQuery: CallbackQuery, message: Message, parts: List<String>, locale: Locale) {
        val radius = SearchRadius.fromMeters(parts.getOrNull(1)?.toIntOrNull())
        val lat = parts.getOrNull(2)?.toDoubleOrNull()
        val lon = parts.getOrNull(3)?.toDoubleOrNull()
        if (lat == null || lon == null) return answer(callbackQuery, null)
        val result = nearbySearchService.searchInChat(message.chatId, lat, lon, radius)
        edit(
            message.chatId, message.messageId, render("nearby/result", nearbyView(result, locale), locale),
            nearbyMarkup(result, lat, lon, locale),
        )
        answer(callbackQuery, null)
    }

    // --- представления -----------------------------------------------------------------------

    private fun listData(chatId: Long, page: Int): ListData {
        val result = publishService.listPublished(chatId, page, PAGE_SIZE)
        val items = result.items.mapIndexed { index, place ->
            ListItemView(result.page * PAGE_SIZE + index + 1, place.name, formatter.shorten(place.address, ADDRESS_PREVIEW))
        }
        return ListData(PlacesListView(items, result.page + 1, result.totalPages, result.totalItems), result.items)
    }

    private fun listMarkup(data: ListData): InlineKeyboardMarkup? {
        val view = data.view
        val rows = mutableListOf<InlineKeyboardRow>()
        data.items.forEachIndexed { index, place ->
            val label = "${view.items[index].index}. ${place.name.take(BUTTON_NAME_LENGTH)}"
            rows += InlineKeyboardRow(callbackButton(label, "O:${place.id}"))
        }
        val nav = mutableListOf<InlineKeyboardButton>()
        if (view.page > 1) nav += callbackButton("◀", "P:${view.page - 2}")
        if (view.page < view.totalPages) nav += callbackButton("▶", "P:${view.page}")
        if (nav.isNotEmpty()) rows += InlineKeyboardRow(nav)
        return rows.takeIf { it.isNotEmpty() }?.let { InlineKeyboardMarkup(it) }
    }

    private fun nearbyView(result: NearbyResult, locale: Locale): NearbyView {
        val shown = result.points.take(MAX_SHOWN)
        return NearbyView(
            radiusText = formatter.distance(result.selected.meters, locale),
            points = shown.mapIndexed { index, point ->
                PointView(index + 1, point.place.name, formatter.distance(point.distanceMeters, locale))
            },
            hints = result.hints.map { HintView(formatter.distance(it.radius.meters, locale), it.extra, it.total) },
            nothingAnywhere = result.points.isEmpty() && result.hints.isEmpty(),
            hiddenCount = result.points.size - shown.size,
        )
    }

    private fun nearbyMarkup(result: NearbyResult, lat: Double, lon: Double, locale: Locale): InlineKeyboardMarkup? {
        val rows = mutableListOf<InlineKeyboardRow>()
        result.points.take(MAX_SHOWN).forEachIndexed { index, point ->
            val distance = formatter.distance(point.distanceMeters, locale)
            val label = "${index + 1}. ${point.place.name.take(BUTTON_NAME_LENGTH)} · $distance"
            rows += InlineKeyboardRow(callbackButton(label, "O:${point.place.id}"))
        }
        val coordinates = String.format(Locale.ROOT, "%.5f:%.5f", lat, lon)
        result.hints.forEach { hint ->
            val radiusText = formatter.distance(hint.radius.meters, locale)
            val label = i18n.i18n("buttons.nearby.hint", locale, null, radiusText, hint.extra, hint.total)
            rows += InlineKeyboardRow(callbackButton(label, "R:${hint.radius.meters}:$coordinates"))
        }
        return rows.takeIf { it.isNotEmpty() }?.let { InlineKeyboardMarkup(it) }
    }

    // --- вспомогательное ---------------------------------------------------------------------

    private fun registerMember(chat: Chat, user: User) {
        runCatching {
            userService.createOrUpdateUser(user)
            chatService.registerChat(chat)
            chatService.linkUser(user.id, chat.id)
        }.onFailure { logger.warn { "Could not register member ${user.id} of chat ${chat.id}: ${it.message}" } }
    }

    private fun localeOf(user: User): Locale = LocaleUtils.resolve(userService.getUser(user.id))

    private fun userRadius(userId: Long): SearchRadius =
        SearchRadius.fromMeters(userService.getUser(userId)?.profile?.settings?.searchRadiusMeters)

    private fun render(template: String, data: Any?, locale: Locale): String =
        messageGenerateService.processTemplate(template, data, locale)

    private fun callbackButton(text: String, data: String): InlineKeyboardButton =
        InlineKeyboardButton(text).apply {
            callbackData = """{"flow":"${GroupPlacesService.CALLBACK_KEY}","data":"$data"}"""
        }

    private fun run(action: String, block: () -> Unit) {
        runCatching { block() }.onFailure { logger.warn { "Group action '$action' failed: ${it.message}" } }
    }

    private fun deleteMessage(chatId: Long, messageId: Int) {
        run("delete message") {
            telegramClient.execute(DeleteMessage.builder().chatId(chatId).messageId(messageId).build())
        }
    }

    private fun edit(chatId: Long, messageId: Int, text: String, markup: InlineKeyboardMarkup?) {
        val method = EditMessageText.builder().chatId(chatId).messageId(messageId).text(text).parseMode(HTML)
            .replyMarkup(markup).build()
        try {
            telegramClient.execute(method)
        } catch (error: TelegramApiRequestException) {
            if (error.apiResponse?.contains("message is not modified") != true) {
                logger.warn { "Group edit failed: ${error.message}" }
            }
        } catch (error: Exception) {
            logger.warn { "Group edit failed: ${error.message}" }
        }
    }

    private fun answer(callbackQuery: CallbackQuery, text: String?, alert: Boolean = false) {
        run("answer callback") {
            telegramClient.execute(
                AnswerCallbackQuery.builder().callbackQueryId(callbackQuery.id).text(text).showAlert(alert).build()
            )
        }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
        const val HTML = "HTML"
        const val PAGE_SIZE = 8
        const val BUTTON_NAME_LENGTH = 35
        const val ADDRESS_PREVIEW = 60
        const val MAX_DESCRIPTION = 600
        const val MAX_CAPTION = 1024
        const val MAX_SHOWN = 8
    }
}
