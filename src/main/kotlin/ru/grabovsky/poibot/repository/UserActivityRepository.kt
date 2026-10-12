package ru.grabovsky.poibot.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.time.LocalDate

/** Дни активности пользователей (`user_activity_day`). */
@Repository
class UserActivityRepository(private val jdbc: JdbcTemplate) {

    /** Отмечает, что пользователь был активен в этот день; повторная отметка ничего не меняет. */
    fun touch(userId: Long, day: LocalDate) {
        jdbc.update(
            "INSERT INTO poi_bot.user_activity_day (user_id, day) VALUES (?, ?) ON CONFLICT DO NOTHING",
            userId, Date.valueOf(day),
        )
    }
}
