CREATE TABLE IF NOT EXISTS web3_admin_action_audits (
    id BIGSERIAL PRIMARY KEY,
    operator_id BIGINT NOT NULL,
    action_type VARCHAR(60) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(100),
    success BOOLEAN NOT NULL,
    detail_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_web3_admin_action_type
        CHECK (action_type IN ('TREASURY_KEY_PROVISION', 'TRANSACTION_MARK_SUCCEEDED')),
    CONSTRAINT ck_web3_admin_target_type
        CHECK (target_type IN ('TREASURY_KEY', 'WEB3_TRANSACTION'))
);

CREATE INDEX IF NOT EXISTS idx_web3_admin_action_audits_created_at
    ON web3_admin_action_audits(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_web3_admin_action_audits_operator_id
    ON web3_admin_action_audits(operator_id, created_at DESC);
