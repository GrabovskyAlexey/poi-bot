--liquibase formatted sql

--changeset poi-bot:share_token_expiry
-- Срок действия ссылки-приглашения; NULL - без срока. Отзыв ссылки - удаление строки.
ALTER TABLE poi_bot.place_share_token
    ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;
