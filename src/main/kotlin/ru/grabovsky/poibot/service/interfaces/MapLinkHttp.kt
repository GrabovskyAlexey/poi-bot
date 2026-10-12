package ru.grabovsky.poibot.service.interfaces

/** Сетевые запросы импорта ссылок на карты. Отделены от разбора, чтобы тестировать его без сети. */
interface MapLinkHttp {
    /** Адрес из заголовка Location (абсолютный) без перехода по нему; null - не редирект или ошибка. */
    fun redirectTarget(url: String): String?

    /** Тело страницы или null при ошибке. */
    fun page(url: String): String?

    /** Короткий адрес «улица, дом, город» по координатам (OpenStreetMap Nominatim) или null. */
    fun reverseAddress(lat: Double, lon: Double): String?
}
