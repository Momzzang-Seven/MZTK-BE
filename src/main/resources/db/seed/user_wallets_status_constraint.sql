-- Seed: user_wallets status constraint normalization
--
-- Problem:
--   Legacy CHECK constraint allows only (ACTIVE, INACTIVE, BLACKLISTED),
--   causing unlink flow (ACTIVE -> UNLINKED) to fail with 500.
--
-- Goal:
--   Align DB constraint with current WalletStatus enum:
--     ACTIVE, UNLINKED, USER_DELETED, BLOCKED, HARD_DELETED

-- Drop legacy constraint (historically named user_wallets_status_check).
ALTER TABLE user_wallets DROP CONSTRAINT IF EXISTS user_wallets_status_check;

-- Migrate legacy values (if any).
UPDATE user_wallets SET status = 'UNLINKED' WHERE CAST(status AS VARCHAR) = 'INACTIVE';
UPDATE user_wallets SET status = 'BLOCKED' WHERE CAST(status AS VARCHAR) = 'BLACKLISTED';

-- Recreate constraint with the current allowed statuses.
ALTER TABLE user_wallets
  ADD CONSTRAINT user_wallets_status_check
  CHECK (status IN ('ACTIVE', 'UNLINKED', 'USER_DELETED', 'BLOCKED', 'HARD_DELETED'));
