-- MOM-444 — Relax web3_treasury_wallets uniqueness for cohort sharing.
--
-- Background
-- ----------
-- Until now treasury_address and kms_key_id were each UNIQUE (V061 / V069), so two distinct
-- wallet_alias rows could never share a KMS key. MOM-444 introduces "cohorts": a set of
-- wallet_alias rows that share one (treasury_address, kms_key_id) pair, while each alias still
-- binds its own KMS key alias 1:1.
--
-- This migration drops the two per-column UNIQUE constraints and replaces the integrity they
-- provided with a pairing-invariant trigger: treasury_address <-> kms_key_id stays 1:1, but the
-- pair may be shared by N rows. wallet_alias remains UNIQUE (row identity) and is untouched here.
--
-- Forward-only
-- ------------
-- Forward-only by policy. Once the first sibling row is inserted (two aliases sharing a pair),
-- the V061/V069 single-row UNIQUE invariant can no longer be restored without data loss. To
-- reverse a deploy before any cohort exists, author a follow-up V0XX that re-adds the UNIQUE
-- constraints and drops the trigger; do NOT flyway:repair this migration away.
--
-- Idempotency
-- -----------
-- DROP CONSTRAINT / DROP TRIGGER use IF EXISTS; CREATE INDEX / CREATE OR REPLACE FUNCTION are
-- naturally re-runnable. The pre-flight integrity check still runs on every (re-)application.

-- [1] Pre-flight integrity check — refuse to relax uniqueness on data that already violates the
--     (treasury_address <-> kms_key_id) 1:1 invariant the trigger is about to enforce. Under the
--     V061/V069 constraints this can only be 0 rows, but a prior raw-SQL operation could have
--     dropped those constraints out of band, so we verify rather than assume.
DO $$
DECLARE
    bad_address_count BIGINT;
    bad_key_count BIGINT;
BEGIN
    SELECT count(*) INTO bad_address_count
    FROM (
        SELECT treasury_address
        FROM web3_treasury_wallets
        GROUP BY treasury_address
        HAVING count(DISTINCT kms_key_id) > 1
    ) violators;

    SELECT count(*) INTO bad_key_count
    FROM (
        SELECT kms_key_id
        FROM web3_treasury_wallets
        GROUP BY kms_key_id
        HAVING count(DISTINCT treasury_address) > 1
    ) violators;

    IF bad_address_count > 0 OR bad_key_count > 0 THEN
        RAISE EXCEPTION
            'V073 aborted: web3_treasury_wallets violates the treasury_address <-> kms_key_id 1:1 invariant '
            '(% addresses mapped to multiple keys, % keys mapped to multiple addresses). '
            'Resolve the mixed cohort via the runbook before applying this migration.',
            bad_address_count, bad_key_count;
    END IF;
END $$;

-- [2] Drop the per-column UNIQUE constraints. treasury_address UNIQUE was named in V061,
--     kms_key_id UNIQUE in V069. After this, both columns are non-unique (cohort-shareable).
ALTER TABLE web3_treasury_wallets
    DROP CONSTRAINT IF EXISTS uk_web3_treasury_wallets_treasury_address;
ALTER TABLE web3_treasury_wallets
    DROP CONSTRAINT IF EXISTS uk_web3_treasury_wallets_kms_key_id;

-- [3] Replace the dropped UNIQUE indexes with plain (non-unique) indexes so cohort lookups
--     (loadAllByTreasuryAddress / address-keyed advisory lock) and key-keyed scans stay fast.
CREATE INDEX IF NOT EXISTS idx_web3_treasury_wallets_treasury_address
    ON web3_treasury_wallets (treasury_address);
CREATE INDEX IF NOT EXISTS idx_web3_treasury_wallets_kms_key_id
    ON web3_treasury_wallets (kms_key_id);

-- [4] Pairing-invariant trigger — DB-level backstop for invariant #1 (treasury_address <-> kms_key_id
--     is 1:1). The application layer also strict-rejects mixed cohorts before reaching here; this
--     trigger catches raw-SQL operational mistakes.
CREATE OR REPLACE FUNCTION assert_treasury_address_key_pairing()
    RETURNS TRIGGER AS $$
BEGIN
    -- same treasury_address already bound to a different kms_key_id
    IF EXISTS (
        SELECT 1 FROM web3_treasury_wallets w
        WHERE w.treasury_address = NEW.treasury_address
          AND w.kms_key_id <> NEW.kms_key_id
          AND w.id IS DISTINCT FROM NEW.id
    ) THEN
        RAISE EXCEPTION
            'treasury_address % is already bound to a different kms_key_id — '
            'cohort rows must share one (treasury_address, kms_key_id) pair',
            NEW.treasury_address;
    END IF;

    -- same kms_key_id already bound to a different treasury_address
    IF EXISTS (
        SELECT 1 FROM web3_treasury_wallets w
        WHERE w.kms_key_id = NEW.kms_key_id
          AND w.treasury_address <> NEW.treasury_address
          AND w.id IS DISTINCT FROM NEW.id
    ) THEN
        RAISE EXCEPTION
            'kms_key_id % is already bound to a different treasury_address — '
            'cohort rows must share one (treasury_address, kms_key_id) pair',
            NEW.kms_key_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_web3_treasury_wallets_pairing ON web3_treasury_wallets;
CREATE TRIGGER trg_web3_treasury_wallets_pairing
    BEFORE INSERT OR UPDATE OF treasury_address, kms_key_id
    ON web3_treasury_wallets
    FOR EACH ROW
    EXECUTE FUNCTION assert_treasury_address_key_pairing();
