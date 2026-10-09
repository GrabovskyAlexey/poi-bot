package ru.grabovsky.poibot.strategy.flow.addplace

import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowStep
import ru.grabovsky.poibot.strategy.flow.core.support.PromptState
import ru.grabovsky.poibot.strategy.flow.places.CardRef

enum class AddPlaceStep(override val key: String) : FlowStep {
    FORM("form"),
    PROMPT("prompt"),
    FIELD_CHOICE("field_choice"),
    RESOLVE("resolve"),
    SAVED("saved"),
}

/** Поля формы. [code] используется в callback-данных. */
enum class PlaceField(val code: String, val maxLength: Int) {
    NAME("name", 100),
    ADDRESS("address", 300),
    LOCATION("location", 0),
    PHOTO("photo", 0),
    WEBSITE("website", 500),
    DESCRIPTION("description", 1000);

    companion object {
        fun fromCode(code: String?): PlaceField? = entries.firstOrNull { it.code == code }
    }
}

data class CandidateDto(
    var placeId: Long = 0,
    var name: String = "",
    var address: String? = null,
    var distance: Int = 0,
    var similarity: Double = 0.0,
)

/** Черновик точки + служебное состояние диалога (хранится как JSON в flow_state). */
data class AddPlaceState(
    var editingId: Long? = null,
    var name: String? = null,
    var address: String? = null,
    var description: String? = null,
    var websiteUrl: String? = null,
    var photoFileId: String? = null,
    var photoFileUniqueId: String? = null,
    var lat: Double? = null,
    var lon: Double? = null,
    var originalLat: Double? = null,
    var originalLon: Double? = null,
    var awaitingField: String? = null,
    var pendingText: String? = null,
    var pendingMessageId: Int? = null,
    var candidates: MutableList<CandidateDto> = mutableListOf(),
    var resolveStrong: Boolean = false,
    /** Карточка, из которой открыто редактирование: после сохранения её перерисовываем. */
    var card: CardRef? = null,
    override val promptBindings: MutableList<String> = mutableListOf(),
) : PromptState {

    fun hasLocation(): Boolean = lat != null && lon != null

    fun geoChanged(): Boolean = lat != originalLat || lon != originalLon

    fun loadFrom(place: SavedPlace) {
        editingId = place.id
        name = place.name
        address = place.address
        description = place.description
        websiteUrl = place.websiteUrl
        photoFileId = place.photoFileId
        photoFileUniqueId = place.photoFileUniqueId
        lat = place.lat
        lon = place.lon
        originalLat = place.lat
        originalLon = place.lon
    }

    fun get(field: PlaceField): String? = when (field) {
        PlaceField.NAME -> name
        PlaceField.ADDRESS -> address
        PlaceField.DESCRIPTION -> description
        PlaceField.WEBSITE -> websiteUrl
        PlaceField.PHOTO -> photoFileId
        PlaceField.LOCATION -> if (hasLocation()) "$lat,$lon" else null
    }

    fun clear(field: PlaceField) {
        when (field) {
            PlaceField.NAME -> name = null
            PlaceField.ADDRESS -> address = null
            PlaceField.DESCRIPTION -> description = null
            PlaceField.WEBSITE -> websiteUrl = null
            PlaceField.PHOTO -> {
                photoFileId = null
                photoFileUniqueId = null
            }
            PlaceField.LOCATION -> {
                lat = null
                lon = null
            }
        }
    }
}

/** Модель шаблона `add_place/form`. */
data class AddFormView(
    val editing: Boolean,
    val name: String?,
    val address: String?,
    val hasLocation: Boolean,
    val hasPhoto: Boolean,
    val website: String?,
    val description: String?,
)

/** Модель шаблона `add_place/prompt`. */
data class PromptView(val field: String, val hasValue: Boolean, val error: String?)

/** Модель шаблона `add_place/field_choice`. */
data class FieldChoiceView(val text: String)

data class CandidateView(val name: String, val address: String?, val distanceText: String)

/** Модель шаблонов `add_place/resolve`. */
data class ResolveView(val strong: Boolean, val candidates: List<CandidateView>)

/** Модель шаблона `add_place/saved`. */
data class SavedView(val name: String, val editing: Boolean)
