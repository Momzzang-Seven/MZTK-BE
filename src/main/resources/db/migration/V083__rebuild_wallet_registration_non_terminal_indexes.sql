-- flyway:executeInTransaction=false

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
