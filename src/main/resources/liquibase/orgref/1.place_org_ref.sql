--liquibase formatted sql

--changeset poi-bot:place_org_ref
-- Идентификатор организации в сервисе карт («yandex:<oid>», «google:<ftid>», «2gis:<id>»): сильный ключ склейки мест.
ALTER TABLE poi_bot.place ADD COLUMN org_ref VARCHAR(100);
CREATE INDEX idx_place_org_ref ON poi_bot.place (org_ref) WHERE org_ref IS NOT NULL;
