-- flyway:executeInTransaction=false

ALTER TABLE web3_wallet_registration_sessions
    DROP CONSTRAINT IF EXISTS ck_web3_wallet_registration_sessions_status;

ALTER TABLE web3_wallet_registration_sessions
    ADD CONSTRAINT ck_web3_wallet_registration_sessions_status
        CHECK (
            status IN (
                'APPROVAL_REQUIRED',
                'APPROVAL_SIGNED',
                'APPROVAL_PENDING_ONCHAIN',
                'APPROVAL_RETRYABLE',
                'SPONSOR_NONCE_BLOCKED',
                'REGISTERED',
                'APPROVAL_FAILED',
                'EXPIRED',
                'CANCELED',
                'FINALIZATION_FAILED',
                'LOCAL_CONFLICT'
            )
        ) NOT VALID;

ALTER TABLE web3_wallet_registration_sessions
    VALIDATE CONSTRAINT ck_web3_wallet_registration_sessions_status;

DROP INDEX CONCURRENTLY IF EXISTS uk_web3_wallet_registration_sessions_non_terminal_user;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_web3_wallet_registration_sessions_non_terminal_user
    ON web3_wallet_registration_sessions(user_id)
    WHERE status IN (
        'APPROVAL_REQUIRED',
        'APPROVAL_SIGNED',
        'APPROVAL_PENDING_ONCHAIN',
        'APPROVAL_RETRYABLE',
        'SPONSOR_NONCE_BLOCKED',
        'FINALIZATION_FAILED',
        'LOCAL_CONFLICT'
    );

DROP INDEX CONCURRENTLY IF EXISTS uk_web3_wallet_registration_sessions_non_terminal_wallet;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_web3_wallet_registration_sessions_non_terminal_wallet
    ON web3_wallet_registration_sessions(wallet_address)
    WHERE status IN (
        'APPROVAL_REQUIRED',
        'APPROVAL_SIGNED',
        'APPROVAL_PENDING_ONCHAIN',
        'APPROVAL_RETRYABLE',
        'SPONSOR_NONCE_BLOCKED',
        'FINALIZATION_FAILED',
        'LOCAL_CONFLICT'
    );
