package ru.grabovsky.poibot.service.interfaces

enum class RelinkResult { RELINKED, NOT_FOUND, SAME_PLACE }

/** Исправление привязки записи к месту и слияние дублей [ru.grabovsky.poibot.entity.Place]. */
interface PlaceLinkService {
    /** Другие места рядом с записью (кроме текущего), к которым её можно привязать. */
    fun candidatesFor(userId: Long, savedPlaceId: Long): List<PlaceCandidate>

    /**
     * Привязывает запись пользователя к другому месту. Оценка пользователя переезжает вместе с записью
     * (если на новом месте её ещё нет). Если на старом месте не осталось записей, оно сливается с новым:
     * комментарии и оценки остальных пользователей переносятся, старое место помечается как слитое.
     */
    fun relink(userId: Long, savedPlaceId: Long, newPlaceId: Long): RelinkResult
}
