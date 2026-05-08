-- MOM-340 / MOM-391 PR4 cleanup (spec V059, slot V065 after develop V063/V064 reservations).
--
-- Finalize the `web3_treasury_wallets` schema for the KMS-only world:
--   * Promote KMS-required columns to NOT NULL (operator backfill must be complete).
--   * Add named UNIQUE constraint on `kms_key_id` (treasury_address UNIQUE was renamed in V061).
--   * Add CHECK constraints to lock the row shape — status / key_origin / KMS-required pair.
--   * DROP the legacy `treasury_private_key_encrypted` column (PR1~PR3 cleared all writers).
--
-- Pre-conditions
-- ---------------
-- Every existing row must have `kms_key_id`, `treasury_address`, `status`, `key_origin`
-- populated. PR1 backfilled `status='ACTIVE'` / `key_origin='IMPORTED'`; operators must run
-- the admin provision endpoint or backfill SQL before deploying this migration in any
-- environment that still carried legacy rows.
--
-- Operator pre-deployment check (run on the target DB before applying V065):
--   SELECT count(*) AS missing_required
--   FROM web3_treasury_wallets
--   WHERE kms_key_id       IS NULL
--      OR treasury_address IS NULL
--      OR status           IS NULL
--      OR key_origin       IS NULL;
--   -- expected: 0
--
--   SELECT key_origin, count(*) FROM web3_treasury_wallets GROUP BY key_origin;
--   -- expected: only 'IMPORTED' (anything else will fail ck_web3_treasury_wallets_key_origin)
--
--   SELECT status, count(*) FROM web3_treasury_wallets GROUP BY status;
--   -- expected: only 'ACTIVE' / 'DISABLED' / 'ARCHIVED'
--
-- Idempotency
-- ------------
-- This migration is written defensively so a manual re-run (e.g. flyway:repair scenarios,
-- staged rollout where a hotfix partially applied the same DDL) does not abort the whole
-- transaction:
--   * SET NOT NULL is naturally a no-op when the column is already NOT NULL.
--   * Each ADD CONSTRAINT is preceded by DROP CONSTRAINT IF EXISTS.
--   * DROP COLUMN uses IF EXISTS.
-- The pre-deployment check above must still pass — defensive DDL does not paper over a
-- missing backfill, it only protects against repeated structural application.
--
-- Rollback
-- --------
-- Forward-only by policy. To reverse a deploy, author a follow-up V0XX that:
--   1. ALTER TABLE web3_treasury_wallets ADD COLUMN treasury_private_key_encrypted TEXT;
--   2. ALTER TABLE web3_treasury_wallets DROP CONSTRAINT IF EXISTS
--        ck_web3_treasury_wallets_kms_key_id_required,
--        ck_web3_treasury_wallets_key_origin,
--        ck_web3_treasury_wallets_status,
--        uk_web3_treasury_wallets_kms_key_id;
--   3. ALTER TABLE web3_treasury_wallets ALTER COLUMN ... DROP NOT NULL (per column).
-- Do NOT use `flyway:repair` to silently retract V065 in prod — leave the audit trail.

ALTER TABLE web3_treasury_wallets
    ALTER COLUMN kms_key_id       SET NOT NULL,
    ALTER COLUMN treasury_address SET NOT NULL,
    ALTER COLUMN status           SET NOT NULL,
    ALTER COLUMN key_origin       SET NOT NULL;

ALTER TABLE web3_treasury_wallets
    DROP CONSTRAINT IF EXISTS uk_web3_treasury_wallets_kms_key_id;
ALTER TABLE web3_treasury_wallets
    ADD CONSTRAINT uk_web3_treasury_wallets_kms_key_id
        UNIQUE (kms_key_id);

ALTER TABLE web3_treasury_wallets
    DROP CONSTRAINT IF EXISTS ck_web3_treasury_wallets_status;
ALTER TABLE web3_treasury_wallets
    ADD CONSTRAINT ck_web3_treasury_wallets_status
        CHECK (status IN ('ACTIVE', 'DISABLED', 'ARCHIVED'));

ALTER TABLE web3_treasury_wallets
    DROP CONSTRAINT IF EXISTS ck_web3_treasury_wallets_key_origin;
ALTER TABLE web3_treasury_wallets
    ADD CONSTRAINT ck_web3_treasury_wallets_key_origin
        CHECK (key_origin IN ('IMPORTED'));

-- KMS-only row shape: every row must carry both kms_key_id and treasury_address.
-- Successor of the V049 ck_web3_treasury_keys_slot_pair CHECK that V061 dropped.
ALTER TABLE web3_treasury_wallets
    DROP CONSTRAINT IF EXISTS ck_web3_treasury_wallets_kms_key_id_required;
ALTER TABLE web3_treasury_wallets
    ADD CONSTRAINT ck_web3_treasury_wallets_kms_key_id_required
        CHECK (kms_key_id IS NOT NULL AND treasury_address IS NOT NULL);

ALTER TABLE web3_treasury_wallets
    DROP COLUMN IF EXISTS treasury_private_key_encrypted;
