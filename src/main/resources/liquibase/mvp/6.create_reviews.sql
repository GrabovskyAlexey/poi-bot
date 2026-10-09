--liquibase formatted sql

--changeset poi-bot:create_place_rating
-- Одна оценка пользователя на место (Place), значение 1..5.
CREATE TABLE poi_bot.place_rating
(
    place_id   BIGINT   NOT NULL REFERENCES poi_bot.place (id),
    user_id    BIGINT   NOT NULL REFERENCES poi_bot.users (id) ON DELETE CASCADE,
    value      SMALLINT NOT NULL CHECK (value BETWEEN 1 AND 5),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (place_id, user_id)
);

--changeset poi-bot:create_place_comment
-- Комментарии общие для места и показываются анонимно; author_id нужен для удаления автором и модерации.
CREATE TABLE poi_bot.place_comment
(
    id         BIGSERIAL PRIMARY KEY,
    place_id   BIGINT        NOT NULL REFERENCES poi_bot.place (id),
    author_id  BIGINT        NOT NULL REFERENCES poi_bot.users (id) ON DELETE CASCADE,
    text       VARCHAR(500)  NOT NULL,
    hidden     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX place_comment_place_idx ON poi_bot.place_comment (place_id, created_at DESC) WHERE hidden = FALSE;
CREATE INDEX place_comment_author_idx ON poi_bot.place_comment (author_id);

--changeset poi-bot:create_place_comment_report
-- Жалобы на комментарии: один пользователь — одна жалоба на комментарий; при достижении порога комментарий скрывается.
CREATE TABLE poi_bot.place_comment_report
(
    comment_id  BIGINT NOT NULL REFERENCES poi_bot.place_comment (id) ON DELETE CASCADE,
    reporter_id BIGINT NOT NULL REFERENCES poi_bot.users (id) ON DELETE CASCADE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (comment_id, reporter_id)
);
