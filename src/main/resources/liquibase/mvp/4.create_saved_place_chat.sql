--liquibase formatted sql

--changeset poi-bot:create_saved_place_chat
-- Публикация записи пользователя в группу. ON UPDATE CASCADE на chat_id — смена id при переходе в супергруппу.
CREATE TABLE poi_bot.saved_place_chat
(
    saved_place_id BIGINT NOT NULL REFERENCES poi_bot.saved_place (id) ON DELETE CASCADE,
    chat_id        BIGINT NOT NULL REFERENCES poi_bot.chat (id) ON UPDATE CASCADE ON DELETE CASCADE,
    shared_by      BIGINT NOT NULL REFERENCES poi_bot.users (id) ON DELETE CASCADE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (saved_place_id, chat_id)
);

CREATE INDEX saved_place_chat_chat_idx ON poi_bot.saved_place_chat (chat_id);
