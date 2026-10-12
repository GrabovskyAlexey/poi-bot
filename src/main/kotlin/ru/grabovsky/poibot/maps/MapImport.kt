package ru.grabovsky.poibot.maps

/** Что удалось достать из ссылки на карты; пустые поля пользователь заполнит сам. */
data class MapImport(
    val name: String?,
    val address: String?,
    val lat: Double,
    val lon: Double,
)
