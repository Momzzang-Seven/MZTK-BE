-- V026__add_location_point_to_trainer_stores.sql

-- Enable PostGIS extension if not already enabled (Requires superuser privileges, assuming it's available or set up in the DB config)
CREATE EXTENSION IF NOT EXISTS postgis;

-- 1. Add new Geometry column (Point, 4326 for WGS 84 coordinate system)
ALTER TABLE trainer_stores ADD COLUMN location geometry(Point, 4326);

-- 2. Populate the new column using existing latitude and longitude data
UPDATE trainer_stores SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326) WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

-- 3. Create Spatial Index (GIST) for fast LBS queries
CREATE INDEX idx_trainer_stores_location ON trainer_stores USING GIST (location);
