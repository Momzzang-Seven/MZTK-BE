-- V032__create_trainer_stores_table.sql
-- Creates the trainer_stores table with PostGIS location support.
-- All constraints are included from the start (no separate ALTER migration needed).

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE trainer_stores (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE,
    store_name      VARCHAR(100) NOT NULL,
    address         VARCHAR(255) NOT NULL,
    detail_address  VARCHAR(255) NOT NULL,
    location        geometry(Point, 4326),
    phone_number    VARCHAR(20) NOT NULL,
    homepage_url    VARCHAR(500),
    instagram_url   VARCHAR(500),
    x_profile_url   VARCHAR(500),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trainer_stores_location ON trainer_stores USING GIST (location);
