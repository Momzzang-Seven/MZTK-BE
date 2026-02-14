-- Update user_wallets_status_check to include all statuses defined in WalletStatus enum
ALTER TABLE user_wallets DROP CONSTRAINT IF EXISTS user_wallets_status_check;

ALTER TABLE user_wallets ADD CONSTRAINT user_wallets_status_check
    CHECK (status IN ('ACTIVE', 'UNLINKED', 'USER_DELETED', 'BLOCKED', 'HARD_DELETED'));
