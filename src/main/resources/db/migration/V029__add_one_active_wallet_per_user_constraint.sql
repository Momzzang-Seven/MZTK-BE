-- Enforce at DB level that each user can have at most one ACTIVE wallet.
-- A partial unique index covers only rows where status = 'ACTIVE',
-- so UNLINKED / USER_DELETED / BLOCKED / HARD_DELETED rows are unrestricted.
CREATE UNIQUE INDEX uk_user_wallets_one_active_per_user
    ON user_wallets (user_id)
    WHERE status = 'ACTIVE';
