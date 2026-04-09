-- Replace web3-scoped admin action audit table with a unified admin_action_audits table.
-- Pre-production: web3_admin_action_audits has no data to preserve, so DROP + CREATE
-- is performed in a single migration.

DROP TABLE IF EXISTS web3_admin_action_audits;

CREATE TABLE IF NOT EXISTS admin_action_audits (
    id          BIGSERIAL    PRIMARY KEY,
    operator_id BIGINT       NOT NULL,
    source      VARCHAR(20)  NOT NULL,
    action_type VARCHAR(60)  NOT NULL,
    target_type VARCHAR(40)  NOT NULL,
    target_id   VARCHAR(100),
    success     BOOLEAN      NOT NULL,
    detail_json TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_admin_action_audits_operator_id
    ON admin_action_audits(operator_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_action_audits_created_at
    ON admin_action_audits(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_action_audits_source
    ON admin_action_audits(source, created_at DESC);
