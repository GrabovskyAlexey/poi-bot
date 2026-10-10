--liquibase formatted sql

--changeset poi-bot:saved_place_personal_fields
-- Личные поля записи: статус («хочу сходить» / «был»), заметка и теги. Видны только владельцу.
ALTER TABLE poi_bot.saved_place
    ADD COLUMN status VARCHAR(10),
    ADD COLUMN note   VARCHAR(500),
    ADD COLUMN tags   JSONB NOT NULL DEFAULT '[]'::jsonb;
