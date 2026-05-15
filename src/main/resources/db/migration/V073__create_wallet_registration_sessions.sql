CREATE TABLE IF NOT EXISTS web3_wallet_registration_sessions (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    wallet_address VARCHAR(42) NOT NULL,
    challenge_nonce VARCHAR(100) NOT NULL,
    status VARCHAR(40) NOT NULL,
    latest_execution_intent_id VARCHAR(100),
    latest_transaction_id BIGINT,
    latest_transaction_hash VARCHAR(66),
    last_execution_status VARCHAR(40),
    last_error_code VARCHAR(120),
    last_error_reason TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    approval_expires_at TIMESTAMP,
    submitted_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    finalized_at TIMESTAMP,
    registered_wallet_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_web3_wallet_registration_sessions_public_id UNIQUE (public_id),
    CONSTRAINT uk_web3_wallet_registration_sessions_challenge_nonce UNIQUE (challenge_nonce),
    CONSTRAINT ck_web3_wallet_registration_sessions_status
        CHECK (
            status IN (
                'APPROVAL_REQUIRED',
                'APPROVAL_SIGNED',
                'APPROVAL_PENDING_ONCHAIN',
                'APPROVAL_RETRYABLE',
                'REGISTERED',
                'APPROVAL_FAILED',
                'EXPIRED',
                'CANCELED',
                'FINALIZATION_FAILED',
                'LOCAL_CONFLICT'
            )
        ),
    CONSTRAINT ck_web3_wallet_registration_sessions_wallet_lowercase
        CHECK (wallet_address = lower(wallet_address))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_web3_wallet_registration_sessions_latest_intent
    ON web3_wallet_registration_sessions(latest_execution_intent_id)
    WHERE latest_execution_intent_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_web3_wallet_registration_sessions_non_terminal_user
    ON web3_wallet_registration_sessions(user_id)
    WHERE status IN (
        'APPROVAL_REQUIRED',
        'APPROVAL_SIGNED',
        'APPROVAL_PENDING_ONCHAIN',
        'APPROVAL_RETRYABLE',
        'FINALIZATION_FAILED',
        'LOCAL_CONFLICT'
    );

CREATE UNIQUE INDEX IF NOT EXISTS uk_web3_wallet_registration_sessions_non_terminal_wallet
    ON web3_wallet_registration_sessions(wallet_address)
    WHERE status IN (
        'APPROVAL_REQUIRED',
        'APPROVAL_SIGNED',
        'APPROVAL_PENDING_ONCHAIN',
        'APPROVAL_RETRYABLE',
        'FINALIZATION_FAILED',
        'LOCAL_CONFLICT'
    );

CREATE INDEX IF NOT EXISTS idx_web3_wallet_registration_sessions_user_status
    ON web3_wallet_registration_sessions(user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_web3_wallet_registration_sessions_wallet_status
    ON web3_wallet_registration_sessions(wallet_address, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_web3_wallet_registration_sessions_latest_tx
    ON web3_wallet_registration_sessions(latest_transaction_id)
    WHERE latest_transaction_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_web3_wallet_registration_sessions_expiry
    ON web3_wallet_registration_sessions(status, approval_expires_at)
    WHERE approval_expires_at IS NOT NULL;
