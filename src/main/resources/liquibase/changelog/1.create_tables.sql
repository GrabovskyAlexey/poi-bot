--liquibase formatted sql

--changeset poi-bot:create_schema
CREATE SCHEMA IF NOT EXISTS poi_bot;

--changeset poi-bot:create_users
CREATE TABLE poi_bot.users
(
    id             BIGINT PRIMARY KEY,
    first_name     VARCHAR,
    last_name      VARCHAR,
    username       VARCHAR,
    language       VARCHAR(16),
    last_action_at TIMESTAMP WITH TIME ZONE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

--changeset poi-bot:create_user_profile
CREATE TABLE poi_bot.user_profile
(
    user_id    BIGINT PRIMARY KEY REFERENCES poi_bot.users (id),
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    is_admin   BOOLEAN NOT NULL DEFAULT FALSE,
    settings   JSONB   NOT NULL DEFAULT '{}'::jsonb,
    locale     VARCHAR(2)
);

--changeset poi-bot:create_flow_state
CREATE TABLE poi_bot.flow_state
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    flow_key         VARCHAR(64)  NOT NULL,
    step_key         VARCHAR(64)  NOT NULL,
    payload          JSONB,
    message_bindings JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX flow_state_user_key_uindex
    ON poi_bot.flow_state (user_id, flow_key);
