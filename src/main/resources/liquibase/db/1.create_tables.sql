--liquibase formatted sql

--changeset poi-bot:create_schema
CREATE SCHEMA IF NOT EXISTS poi_bot;

--chageset poi-bot:create_tables
CREATE TABLE IF NOT EXISTS poi_bot.user
(
    user_id     bigint PRIMARY KEY,
    first_name  VARCHAR(255),
    last_name  VARCHAR(255),
    username  VARCHAR(255),
    language VARCHAR(10),
    is_premium  BOOLEAN
);

CREATE TABLE IF NOT EXISTS poi_bot.chat
(
    chat_id     bigint PRIMARY KEY,
    first_name  VARCHAR(255),
    last_name  VARCHAR(255),
    username  VARCHAR(255),
    title VARCHAR(255),
    type VARCHAR(25)
);
--changeset poi-bot:create_table_state
CREATE TABLE IF NOT EXISTS poi_bot.state
(
    id uuid PRIMARY KEY NOT NULL,
    chat_id bigint NOT NULL,
    user_id  bigint NOT NULL,
    state VARCHAR(255),
    poi_data TEXT,
    callback_data TEXT
);
create unique index chat_id_user_id_state on poi_bot.state (chat_id, user_id);

--changeset poi-bot:create_table_poi
CREATE TABLE IF NOT EXISTS poi_bot.poi
(
    id uuid PRIMARY KEY NOT NULL,
    chat_id bigint NOT NULL,
    user_id  bigint NOT NULL,
    name VARCHAR(255),
    description TEXT,
    address TEXT,
    latitude DECIMAL(9, 6),
    longitude DECIMAL(9, 6)
);