CREATE TABLE IF NOT EXISTS web3_treasury_keys (
    id SMALLINT PRIMARY KEY DEFAULT 1,
    treasury_address VARCHAR(42) NOT NULL UNIQUE,
    treasury_private_key_encrypted TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_web3_treasury_keys_singleton CHECK (id = 1)
);

CREATE TABLE IF NOT EXISTS web3_transactions (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    reference_type VARCHAR(30) NOT NULL,
    reference_id VARCHAR(100) NOT NULL,
    from_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    to_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    from_address VARCHAR(42) NOT NULL,
    to_address VARCHAR(42) NOT NULL,
    amount_wei NUMERIC(78, 0) NOT NULL CHECK (amount_wei >= 0),
    nonce BIGINT,
    status VARCHAR(20) NOT NULL,
    tx_hash VARCHAR(66),
    signed_at TIMESTAMP,
    broadcasted_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    signed_raw_tx TEXT,
    failure_reason TEXT,
    processing_until TIMESTAMP,
    processing_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_web3_tx_status
        CHECK (status IN ('CREATED','SIGNED','PENDING','SUCCEEDED','FAILED_ONCHAIN','UNCONFIRMED')),
    CONSTRAINT ck_web3_tx_reference_type
        CHECK (reference_type IN ('LEVEL_UP_REWARD','USER_TO_USER','USER_TO_SERVER','SERVER_TO_USER')),
    CONSTRAINT ck_web3_tx_hash_format
        CHECK (tx_hash IS NULL OR tx_hash ~ '^0x[0-9a-fA-F]{64}$'),
    CONSTRAINT ck_web3_tx_hash_required_by_status
        CHECK (
            (status = 'CREATED' AND tx_hash IS NULL)
            OR (status = 'SIGNED')
            OR (status IN ('PENDING','SUCCEEDED','FAILED_ONCHAIN','UNCONFIRMED') AND tx_hash IS NOT NULL)
        ),
    CONSTRAINT ck_web3_tx_signed_at_required_by_status
        CHECK ((status = 'CREATED') = (signed_at IS NULL)),
    CONSTRAINT ck_web3_tx_broadcasted_at_required_by_status
        CHECK ((status IN ('CREATED','SIGNED')) = (broadcasted_at IS NULL)),
    CONSTRAINT ck_web3_tx_confirmed_at_required_by_status
        CHECK ((status IN ('SUCCEEDED','FAILED_ONCHAIN')) = (confirmed_at IS NOT NULL)),
    CONSTRAINT ck_web3_tx_signed_raw_required
        CHECK (status <> 'SIGNED' OR signed_raw_tx IS NOT NULL),
    CONSTRAINT ck_web3_tx_unconfirmed_reason_required
        CHECK (status <> 'UNCONFIRMED' OR failure_reason IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_web3_tx_reference
    ON web3_transactions(reference_type, reference_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_web3_tx_tx_hash
    ON web3_transactions(tx_hash)
    WHERE tx_hash IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_web3_tx_sender_nonce
    ON web3_transactions(from_address, nonce)
    WHERE nonce IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_web3_tx_status
    ON web3_transactions(status);

CREATE INDEX IF NOT EXISTS idx_web3_tx_processing_until
    ON web3_transactions(processing_until);

CREATE TABLE IF NOT EXISTS web3_nonce_state (
    from_address VARCHAR(42) PRIMARY KEY,
    next_nonce BIGINT NOT NULL CHECK (next_nonce >= 0),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS web3_transaction_audits (
    id BIGSERIAL PRIMARY KEY,
    web3_transaction_id BIGINT NOT NULL REFERENCES web3_transactions(id) ON DELETE CASCADE,
    event_type VARCHAR(30) NOT NULL,
    rpc_alias VARCHAR(10),
    detail_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_web3_tx_audit_event_type CHECK (
        event_type IN (
            'PREVALIDATE',
            'SIGN',
            'BROADCAST',
            'RECEIPT_POLL',
            'STATE_CHANGE',
            'CS_OVERRIDE',
            'AUTHORIZATION',
            'LIMIT_CHECK'
        )
    ),
    CONSTRAINT ck_web3_tx_audit_rpc_alias CHECK (rpc_alias IS NULL OR rpc_alias IN ('main','sub'))
);

CREATE INDEX IF NOT EXISTS idx_web3_tx_audits_txid_created_at
    ON web3_transaction_audits(web3_transaction_id, created_at DESC);
