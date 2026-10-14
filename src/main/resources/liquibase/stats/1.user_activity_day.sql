--liquibase formatted sql

--changeset poi-bot:user_activity_day
-- Дни активности пользователей (по МСК): по таблице считаются DAU/WAU/MAU и история аудитории для /admin.
CREATE TABLE poi_bot.user_activity_day
(
    user_id BIGINT NOT NULL REFERENCES poi_bot.users (id) ON DELETE CASCADE,
    day     DATE   NOT NULL,
    PRIMARY KEY (user_id, day)
);

CREATE INDEX user_activity_day_day_idx ON poi_bot.user_activity_day (day);

--changeset poi-bot:user_activity_day_backfill
-- Прошлая активность неизвестна: берём только последнее действие каждого пользователя (приближение).
INSERT INTO poi_bot.user_activity_day (user_id, day)
SELECT id, (last_action_at AT TIME ZONE 'Europe/Moscow')::date
FROM poi_bot.users
WHERE last_action_at IS NOT NULL
ON CONFLICT DO NOTHING;
