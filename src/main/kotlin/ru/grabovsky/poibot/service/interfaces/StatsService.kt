package ru.grabovsky.poibot.service.interfaces

import ru.grabovsky.poibot.stats.StatsSnapshot
import java.time.Instant

/** Статистика бота для администратора (`/admin`). */
interface StatsService {
    fun snapshot(now: Instant = Instant.now()): StatsSnapshot

    /** Отмечает день активности пользователя; не чаще раза в день на пользователя. */
    fun recordActivity(userId: Long, previousActionAt: Instant?, now: Instant = Instant.now())

    companion object {
        /** Сколько последних недель показывать в истории. */
        const val WEEKS = 8
    }
}
