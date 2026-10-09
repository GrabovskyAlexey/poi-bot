--liquibase formatted sql

--changeset poi-bot:create_place_share_token
-- Одна ссылка на запись: повторное "Поделиться" возвращает тот же токен. Удаление записи делает ссылку недействительной.
CREATE TABLE poi_bot.place_share_token
(
    token          VARCHAR(32) PRIMARY KEY,
    saved_place_id BIGINT NOT NULL UNIQUE REFERENCES poi_bot.saved_place (id) ON DELETE CASCADE,
    created_by     BIGINT NOT NULL REFERENCES poi_bot.users (id) ON DELETE CASCADE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
