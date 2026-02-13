ALTER TABLE web3_transactions
    ADD COLUMN IF NOT EXISTS tx_type VARCHAR(20) NOT NULL DEFAULT 'EIP1559',
    ADD COLUMN IF NOT EXISTS authority_address VARCHAR(42),
    ADD COLUMN IF NOT EXISTS authorization_nonce BIGINT,
    ADD COLUMN IF NOT EXISTS delegate_target VARCHAR(42),
    ADD COLUMN IF NOT EXISTS authorization_expires_at TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_web3_tx_type'
    ) THEN
        ALTER TABLE web3_transactions
            ADD CONSTRAINT ck_web3_tx_type
                CHECK (tx_type IN ('EIP1559', 'EIP7702'));
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_web3_tx_eip7702_authority_nonce
    ON web3_transactions(authority_address, authorization_nonce)
    WHERE tx_type = 'EIP7702' AND authority_address IS NOT NULL AND authorization_nonce IS NOT NULL;

CREATE TABLE IF NOT EXISTS web3_transfer_prepares (
    prepare_id VARCHAR(36) PRIMARY KEY,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT,
    reference_type VARCHAR(30) NOT NULL,
    reference_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    authority_address VARCHAR(42) NOT NULL,
    to_address VARCHAR(42) NOT NULL,
    amount_wei NUMERIC(78, 0) NOT NULL CHECK (amount_wei > 0),
    authority_nonce BIGINT NOT NULL CHECK (authority_nonce >= 0),
    delegate_target VARCHAR(42) NOT NULL,
    auth_expires_at TIMESTAMP NOT NULL,
    payload_hash_to_sign VARCHAR(66) NOT NULL,
    salt VARCHAR(66) NOT NULL,
    status VARCHAR(20) NOT NULL,
    submitted_tx_id BIGINT REFERENCES web3_transactions(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_web3_transfer_prepare_reference_type
        CHECK (reference_type IN ('USER_TO_USER', 'USER_TO_SERVER')),
    CONSTRAINT ck_web3_transfer_prepare_status
        CHECK (status IN ('CREATED', 'SUBMITTED', 'EXPIRED')),
    CONSTRAINT ck_web3_transfer_prepare_payload_hash
        CHECK (payload_hash_to_sign ~ '^0x[0-9a-fA-F]{64}$'),
    CONSTRAINT ck_web3_transfer_prepare_salt
        CHECK (salt ~ '^0x[0-9a-fA-F]{64}$')
);

CREATE INDEX IF NOT EXISTS idx_web3_transfer_prepare_reference
    ON web3_transfer_prepares(reference_type, reference_id);

CREATE INDEX IF NOT EXISTS idx_web3_transfer_prepare_idem_created
    ON web3_transfer_prepares(idempotency_key, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_web3_transfer_prepare_expires_at
    ON web3_transfer_prepares(auth_expires_at);

CREATE TABLE IF NOT EXISTS web3_sponsor_daily_usage (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    usage_date_kst DATE NOT NULL,
    estimated_cost_wei NUMERIC(78, 0) NOT NULL CHECK (estimated_cost_wei >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_web3_sponsor_daily_usage UNIQUE (user_id, usage_date_kst)
);

CREATE INDEX IF NOT EXISTS idx_web3_sponsor_daily_usage_date
    ON web3_sponsor_daily_usage(usage_date_kst);
