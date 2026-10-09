--liquibase formatted sql

--changeset poi-bot:one_comment_per_user
-- Один комментарий пользователя на место (его можно редактировать): оставляем самый свежий и добавляем уникальность.
DELETE FROM poi_bot.place_comment c
    USING poi_bot.place_comment newer
WHERE c.place_id = newer.place_id
  AND c.author_id = newer.author_id
  AND c.id < newer.id;

CREATE UNIQUE INDEX place_comment_place_author_uidx ON poi_bot.place_comment (place_id, author_id);
