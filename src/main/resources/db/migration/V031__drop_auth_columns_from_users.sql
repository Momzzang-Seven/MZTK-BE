-- Phase 5: Remove authentication/lifecycle columns from users table.
-- These columns have been migrated to users_account (created in V030).

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_provider_provider_user_id_key;

ALTER TABLE users DROP COLUMN IF EXISTS provider;
ALTER TABLE users DROP COLUMN IF EXISTS provider_user_id;
ALTER TABLE users DROP COLUMN IF EXISTS password_hash;
ALTER TABLE users DROP COLUMN IF EXISTS google_refresh_token;
ALTER TABLE users DROP COLUMN IF EXISTS status;
ALTER TABLE users DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE users DROP COLUMN IF EXISTS last_login_at;
