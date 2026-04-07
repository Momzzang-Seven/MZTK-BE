-- V030__trainer_store_schema_alignment.sql
-- Aligns trainer_stores schema with domain model constraints.

-- 1. Rename x_url to x_profile_url for clarity
ALTER TABLE trainer_stores RENAME COLUMN x_url TO x_profile_url;

-- 2. Ensure store_name is NOT NULL (backfill any NULLs with empty string first)
UPDATE trainer_stores SET store_name = '' WHERE store_name IS NULL;
ALTER TABLE trainer_stores ALTER COLUMN store_name SET NOT NULL;

-- 3. Ensure detail_address is NOT NULL (backfill any NULLs with empty string first)
UPDATE trainer_stores SET detail_address = '' WHERE detail_address IS NULL;
ALTER TABLE trainer_stores ALTER COLUMN detail_address SET NOT NULL;

-- 4. Ensure phone_number is NOT NULL (backfill any NULLs with 'N/A' first)
UPDATE trainer_stores SET phone_number = 'N/A' WHERE phone_number IS NULL;
ALTER TABLE trainer_stores ALTER COLUMN phone_number SET NOT NULL;

-- 5. Add VARCHAR length constraints
ALTER TABLE trainer_stores ALTER COLUMN store_name TYPE varchar(100);
ALTER TABLE trainer_stores ALTER COLUMN address TYPE varchar(255);
ALTER TABLE trainer_stores ALTER COLUMN detail_address TYPE varchar(255);
ALTER TABLE trainer_stores ALTER COLUMN phone_number TYPE varchar(20);
ALTER TABLE trainer_stores ALTER COLUMN homepage_url TYPE varchar(500);
ALTER TABLE trainer_stores ALTER COLUMN instagram_url TYPE varchar(500);
ALTER TABLE trainer_stores ALTER COLUMN x_profile_url TYPE varchar(500);
