package ru.grabovsky.poibot.maps

/** Что удалось достать из ссылки на карты; пустые поля пользователь заполнит сам. */
data class MapImport(
    val name: String?,
    val address: String?,
    val lat: Double,
    val lon: Double,
    /** Id организации в сервисе карт (`yandex:<oid>`, `google:<ftid>`, `2gis:<id>`), если он есть в ссылке. */
    val orgRef: String? = null,
)
