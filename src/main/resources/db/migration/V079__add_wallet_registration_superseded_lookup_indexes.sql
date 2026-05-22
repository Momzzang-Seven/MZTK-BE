CREATE INDEX IF NOT EXISTS idx_web3_wallet_registration_sessions_user_created_id
    ON web3_wallet_registration_sessions(user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_web3_wallet_registration_sessions_wallet_created_id
    ON web3_wallet_registration_sessions(wallet_address, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_web3_wallet_registration_sessions_user_id_desc
    ON web3_wallet_registration_sessions(user_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_web3_wallet_registration_sessions_wallet_id_desc
    ON web3_wallet_registration_sessions(wallet_address, id DESC);

CREATE INDEX IF NOT EXISTS idx_web3_wallet_registration_sessions_status_updated_id
    ON web3_wallet_registration_sessions(status, updated_at ASC, id ASC);

ALTER TABLE web3_wallet_registration_sessions
    ADD COLUMN receipt_timeout_execution_intent_ids TEXT;

UPDATE web3_wallet_registration_sessions
SET receipt_timeout_execution_intent_ids = latest_execution_intent_id
WHERE last_error_code = 'RECEIPT_TIMEOUT'
  AND latest_execution_intent_id IS NOT NULL
  AND latest_execution_intent_id <> ''
  AND (
    receipt_timeout_execution_intent_ids IS NULL
    OR receipt_timeout_execution_intent_ids = ''
  );
