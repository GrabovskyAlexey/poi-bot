--liquibase formatted sql

--changeset poi-bot:create_place
CREATE TABLE poi_bot.place
(
    id             BIGSERIAL PRIMARY KEY,
    lat            DOUBLE PRECISION,
    lon            DOUBLE PRECISION,
    display_name   VARCHAR(100) NOT NULL,
    address        VARCHAR(300),
    merged_into_id BIGINT REFERENCES poi_bot.place (id),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX place_geo_idx ON poi_bot.place (lat, lon) WHERE merged_into_id IS NULL;

--changeset poi-bot:create_saved_place
CREATE TABLE poi_bot.saved_place
(
    id                   BIGSERIAL PRIMARY KEY,
    owner_id             BIGINT       NOT NULL REFERENCES poi_bot.users (id) ON DELETE CASCADE,
    place_id             BIGINT       NOT NULL REFERENCES poi_bot.place (id),
    name                 VARCHAR(100) NOT NULL,
    address              VARCHAR(300),
    description          VARCHAR(1000),
    website_url          VARCHAR(500),
    photo_file_id        VARCHAR(255),
    photo_file_unique_id VARCHAR(100),
    lat                  DOUBLE PRECISION,
    lon                  DOUBLE PRECISION,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX saved_place_owner_idx ON poi_bot.saved_place (owner_id, created_at DESC);
CREATE INDEX saved_place_owner_geo_idx ON poi_bot.saved_place (owner_id, lat, lon);
CREATE INDEX saved_place_place_idx ON poi_bot.saved_place (place_id);
