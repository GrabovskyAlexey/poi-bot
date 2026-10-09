--liquibase formatted sql

--changeset poi-bot:create_chat
CREATE TABLE poi_bot.chat
(
    id         BIGINT PRIMARY KEY,
    type       VARCHAR(32) NOT NULL,
    title      VARCHAR,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

--changeset poi-bot:create_user_chat
-- ON UPDATE CASCADE: при миграции группы в супергруппу меняется chat.id, ссылки обновляются каскадом
CREATE TABLE poi_bot.user_chat
(
    user_id      BIGINT NOT NULL REFERENCES poi_bot.users (id) ON DELETE CASCADE,
    chat_id      BIGINT NOT NULL REFERENCES poi_bot.chat (id) ON UPDATE CASCADE ON DELETE CASCADE,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, chat_id)
);

CREATE INDEX user_chat_chat_id_idx ON poi_bot.user_chat (chat_id);
