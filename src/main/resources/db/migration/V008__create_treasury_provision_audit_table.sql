CREATE TABLE IF NOT EXISTS web3_treasury_provision_audits (
    id BIGSERIAL PRIMARY KEY,
    operator_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    treasury_address VARCHAR(42),
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_web3_treasury_provision_audit_address_format
        CHECK (treasury_address IS NULL OR treasury_address ~ '^0x[0-9a-fA-F]{40}$')
);

CREATE INDEX IF NOT EXISTS idx_web3_treasury_provision_audits_created_at
    ON web3_treasury_provision_audits(created_at DESC);
