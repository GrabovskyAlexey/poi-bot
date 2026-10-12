package ru.grabovsky.poibot.service.interfaces

import ru.grabovsky.poibot.maps.MapImport

/** Импорт места из ссылки на Google Maps, Яндекс Карты или 2GIS. */
interface MapLinkImportService {
    /** null - в тексте нет ссылки на карты или достать из неё ничего не удалось. */
    fun import(text: String): MapImport?
}
