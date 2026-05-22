CREATE INDEX IF NOT EXISTS idx_web3_wallet_registration_sessions_user_created_id
    ON web3_wallet_registration_sessions(user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_web3_wallet_registration_sessions_wallet_created_id
    ON web3_wallet_registration_sessions(wallet_address, created_at DESC, id DESC);

ALTER TABLE web3_wallet_registration_sessions
    ADD COLUMN receipt_timeout_execution_intent_ids TEXT;
