CREATE TABLE IF NOT EXISTS web3_execution_intents (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    root_idempotency_key VARCHAR(250) NOT NULL,
    attempt_no INTEGER NOT NULL CHECK (attempt_no > 0),
    resource_type VARCHAR(40) NOT NULL,
    resource_id VARCHAR(250) NOT NULL,
    action_type VARCHAR(60) NOT NULL,
    requester_user_id BIGINT NOT NULL,
    counterparty_user_id BIGINT,
    mode VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'AWAITING_SIGNATURE',
    payload_hash VARCHAR(66) NOT NULL,
    payload_snapshot_json TEXT,
    authority_address VARCHAR(42),
    authority_nonce BIGINT CHECK (authority_nonce IS NULL OR authority_nonce >= 0),
    delegate_target VARCHAR(42),
    expires_at TIMESTAMP NOT NULL,
    authorization_payload_hash VARCHAR(66),
    execution_digest VARCHAR(66),
    unsigned_tx_snapshot TEXT,
    unsigned_tx_fingerprint VARCHAR(66),
    reserved_sponsor_cost_wei NUMERIC(78, 0) NOT NULL DEFAULT 0,
    sponsor_usage_date_kst DATE NOT NULL,
    submitted_tx_id BIGINT REFERENCES web3_transactions(id) ON DELETE SET NULL,
    last_error_code VARCHAR(120),
    last_error_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_web3_execution_intents_public_id UNIQUE (public_id),
    CONSTRAINT uk_web3_execution_intents_root_attempt UNIQUE (root_idempotency_key, attempt_no),
    CONSTRAINT uk_web3_execution_intents_submitted_tx UNIQUE (submitted_tx_id),
    CONSTRAINT ck_web3_execution_intents_mode
        CHECK (mode IN ('EIP7702', 'EIP1559')),
    CONSTRAINT ck_web3_execution_intents_status
        CHECK (
            status IN (
                'AWAITING_SIGNATURE',
                'SIGNED',
                'PENDING_ONCHAIN',
                'CONFIRMED',
                'FAILED_ONCHAIN',
                'EXPIRED',
                'CANCELED',
                'NONCE_STALE'
            )
        ),
    CONSTRAINT ck_web3_execution_intents_reserved_cost
        CHECK (reserved_sponsor_cost_wei >= 0)
);

CREATE INDEX IF NOT EXISTS idx_web3_execution_intents_resource
    ON web3_execution_intents(resource_type, resource_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_web3_execution_intents_root_status
    ON web3_execution_intents(root_idempotency_key, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_web3_execution_intents_cleanup
    ON web3_execution_intents(status, expires_at, updated_at);

CREATE INDEX IF NOT EXISTS idx_web3_execution_intents_requester_status
    ON web3_execution_intents(requester_user_id, status, created_at DESC);

