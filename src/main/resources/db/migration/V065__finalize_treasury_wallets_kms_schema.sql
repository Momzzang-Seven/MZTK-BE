-- MOM-340 / MOM-391 PR4 cleanup (spec V059, slot V065 after develop V063/V064 reservations).
--
-- Finalize the `web3_treasury_wallets` schema for the KMS-only world:
--   * Promote KMS-required columns to NOT NULL (operator backfill must be complete).
--   * Add named UNIQUE constraint on `kms_key_id` (treasury_address UNIQUE was renamed in V061).
--   * Add CHECK constraints to lock the row shape — status / key_origin / KMS-required pair.
--   * DROP the legacy `treasury_private_key_encrypted` column (PR1~PR3 cleared all writers).
--
-- This migration assumes every existing row has `kms_key_id`, `wallet_address`, `status`,
-- `key_origin` already populated. PR1 backfilled `status='ACTIVE'` / `key_origin='IMPORTED'`,
-- and operators must run the admin provision endpoint or backfill SQL before deploying this
-- migration in any environment that still carried legacy rows.

ALTER TABLE web3_treasury_wallets
    ALTER COLUMN kms_key_id       SET NOT NULL,
    ALTER COLUMN treasury_address SET NOT NULL,
    ALTER COLUMN status           SET NOT NULL,
    ALTER COLUMN key_origin       SET NOT NULL;

ALTER TABLE web3_treasury_wallets
    ADD CONSTRAINT uk_web3_treasury_wallets_kms_key_id
        UNIQUE (kms_key_id);

ALTER TABLE web3_treasury_wallets
    ADD CONSTRAINT ck_web3_treasury_wallets_status
        CHECK (status IN ('ACTIVE', 'DISABLED', 'ARCHIVED'));

ALTER TABLE web3_treasury_wallets
    ADD CONSTRAINT ck_web3_treasury_wallets_key_origin
        CHECK (key_origin IN ('IMPORTED'));

-- KMS-only row shape: every row must carry both kms_key_id and treasury_address.
-- Successor of the V049 ck_web3_treasury_keys_slot_pair CHECK that V061 dropped.
ALTER TABLE web3_treasury_wallets
    ADD CONSTRAINT ck_web3_treasury_wallets_kms_key_id_required
        CHECK (kms_key_id IS NOT NULL AND treasury_address IS NOT NULL);

ALTER TABLE web3_treasury_wallets
    DROP COLUMN treasury_private_key_encrypted;
