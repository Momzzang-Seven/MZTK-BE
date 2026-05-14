-- MOM-444 — Add wallet_alias to web3_treasury_provision_audits for alias-level auditing.
--
-- Background
-- ----------
-- With cohort provisioning, a single lifecycle operation can touch N wallet_alias rows that share
-- one (treasury_address, kms_key_id) pair. Audit now records one row per alias (alias-level audit
-- N times) plus the KMS-level audit (web3_treasury_kms_audits) once per cohort. This column lets a
-- provision audit row identify which alias it belongs to.
--
-- The column is nullable and historical rows are NOT backfilled: rows written before MOM-444 were
-- single-row operations with no cohort concept, so a NULL wallet_alias cleanly distinguishes them
-- from the alias-level rows written afterward.
--
-- Forward-only by policy. To reverse, author a follow-up V0XX that drops the column.

ALTER TABLE web3_treasury_provision_audits
    ADD COLUMN IF NOT EXISTS wallet_alias VARCHAR(64);
