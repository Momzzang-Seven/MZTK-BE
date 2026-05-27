-- MOM-458: bind normalized transaction nonce scopes and backfill sponsor nonce slots.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_web3_tx_id_chain_sender_nonce'
    ) THEN
        ALTER TABLE web3_transactions
            ADD CONSTRAINT uk_web3_tx_id_chain_sender_nonce
            UNIQUE USING INDEX uk_web3_tx_id_chain_sender_nonce;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_web3_tx_addresses_lower'
    ) THEN
        ALTER TABLE web3_transactions
            ADD CONSTRAINT ck_web3_tx_addresses_lower
            CHECK (
                from_address = LOWER(from_address)
                AND to_address = LOWER(to_address)
                AND (authority_address IS NULL OR authority_address = LOWER(authority_address))
                AND (delegate_target IS NULL OR delegate_target = LOWER(delegate_target))
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_web3_nonce_state_from_lower'
    ) THEN
        ALTER TABLE web3_nonce_state
            ADD CONSTRAINT ck_web3_nonce_state_from_lower
            CHECK (from_address = LOWER(from_address)) NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_web3_tx_addresses_lower'
          AND conrelid = 'web3_transactions'::regclass
          AND NOT convalidated
    ) THEN
        ALTER TABLE web3_transactions VALIDATE CONSTRAINT ck_web3_tx_addresses_lower;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_web3_nonce_state_from_lower'
          AND conrelid = 'web3_nonce_state'::regclass
          AND NOT convalidated
    ) THEN
        ALTER TABLE web3_nonce_state VALIDATE CONSTRAINT ck_web3_nonce_state_from_lower;
    END IF;
END $$;

INSERT INTO web3_sponsor_nonce_locks(chain_id, from_address, created_at, updated_at)
SELECT DISTINCT chain_id, from_address, NOW(), NOW()
FROM web3_transactions
WHERE nonce IS NOT NULL
ON CONFLICT (chain_id, from_address) DO NOTHING;

INSERT INTO web3_nonce_slot_attempts(
    chain_id,
    from_address,
    nonce,
    attempt_no,
    tx_id,
    tx_hash,
    status,
    idempotency_key,
    terminal_reason,
    signed_at,
    broadcasted_at,
    created_at,
    updated_at
)
SELECT
    t.chain_id,
    t.from_address,
    t.nonce,
    1,
    t.id,
    CASE
        WHEN t.status = 'CREATED' THEN NULL
        ELSE t.tx_hash
    END,
    CASE
        WHEN t.status = 'CREATED' THEN 'RESERVED'
        WHEN t.status = 'SIGNED' THEN 'SIGNED'
        WHEN t.status = 'UNCONFIRMED'
             AND t.failure_reason LIKE 'RECEIPT_TIMEOUT%' THEN 'STUCK'
        ELSE 'BROADCASTED'
    END,
    CONCAT('tx:', t.id, ':sponsor:', t.nonce, ':attempt:1'),
    CASE
        WHEN t.status = 'UNCONFIRMED'
             AND t.failure_reason LIKE 'RECEIPT_TIMEOUT%' THEN t.failure_reason
        ELSE NULL
    END,
    CASE
        WHEN t.status = 'CREATED' THEN NULL
        ELSE t.signed_at
    END,
    CASE
        WHEN t.status IN ('PENDING', 'UNCONFIRMED') THEN t.broadcasted_at
        ELSE NULL
    END,
    t.created_at,
    t.updated_at
FROM web3_transactions t
WHERE t.nonce IS NOT NULL
  AND t.status IN ('CREATED', 'SIGNED', 'PENDING', 'UNCONFIRMED')
ON CONFLICT (tx_id) DO NOTHING;

INSERT INTO web3_nonce_slots(
    chain_id,
    from_address,
    nonce,
    status,
    attempt_no,
    active_attempt_id,
    active_tx_id,
    active_tx_hash,
    last_broadcasted_at,
    stuck_reason,
    replacement_prepare_attempt_count,
    broadcast_recovery_attempt_count,
    created_at,
    updated_at
)
SELECT
    t.chain_id,
    t.from_address,
    t.nonce,
    CASE
        WHEN t.status = 'CREATED' THEN 'RESERVED'
        WHEN t.status = 'SIGNED' THEN 'SIGNED'
        WHEN t.status = 'UNCONFIRMED'
             AND t.failure_reason LIKE 'RECEIPT_TIMEOUT%' THEN 'STUCK'
        ELSE 'BROADCASTED'
    END,
    a.attempt_no,
    a.id,
    t.id,
    CASE
        WHEN t.status = 'CREATED' THEN NULL
        ELSE t.tx_hash
    END,
    CASE
        WHEN t.status IN ('PENDING', 'UNCONFIRMED') THEN t.broadcasted_at
        ELSE NULL
    END,
    CASE
        WHEN t.status = 'UNCONFIRMED'
             AND t.failure_reason LIKE 'RECEIPT_TIMEOUT%' THEN t.failure_reason
        ELSE NULL
    END,
    0,
    0,
    t.created_at,
    t.updated_at
FROM web3_transactions t
JOIN web3_nonce_slot_attempts a
    ON a.tx_id = t.id
WHERE t.nonce IS NOT NULL
  AND t.status IN ('CREATED', 'SIGNED', 'PENDING', 'UNCONFIRMED')
ON CONFLICT (chain_id, from_address, nonce) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_web3_nonce_slot_attempt_tx_scope'
    ) THEN
        ALTER TABLE web3_nonce_slot_attempts
            ADD CONSTRAINT fk_web3_nonce_slot_attempt_tx_scope
            FOREIGN KEY (tx_id, chain_id, from_address, nonce)
            REFERENCES web3_transactions(id, chain_id, from_address, nonce)
            ON DELETE RESTRICT
            NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_web3_nonce_slots_active_tx'
    ) THEN
        ALTER TABLE web3_nonce_slots
            ADD CONSTRAINT fk_web3_nonce_slots_active_tx
            FOREIGN KEY (active_tx_id, chain_id, from_address, nonce)
            REFERENCES web3_transactions(id, chain_id, from_address, nonce)
            ON DELETE RESTRICT
            NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_web3_nonce_slots_consumed_tx'
    ) THEN
        ALTER TABLE web3_nonce_slots
            ADD CONSTRAINT fk_web3_nonce_slots_consumed_tx
            FOREIGN KEY (consumed_tx_id, chain_id, from_address, nonce)
            REFERENCES web3_transactions(id, chain_id, from_address, nonce)
            ON DELETE RESTRICT
            NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_web3_nonce_slots_released_tx'
    ) THEN
        ALTER TABLE web3_nonce_slots
            ADD CONSTRAINT fk_web3_nonce_slots_released_tx
            FOREIGN KEY (released_tx_id, chain_id, from_address, nonce)
            REFERENCES web3_transactions(id, chain_id, from_address, nonce)
            ON DELETE RESTRICT
            NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_web3_nonce_slot_attempt_tx_scope'
          AND conrelid = 'web3_nonce_slot_attempts'::regclass
          AND NOT convalidated
    ) THEN
        ALTER TABLE web3_nonce_slot_attempts
            VALIDATE CONSTRAINT fk_web3_nonce_slot_attempt_tx_scope;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_web3_nonce_slots_active_tx'
          AND conrelid = 'web3_nonce_slots'::regclass
          AND NOT convalidated
    ) THEN
        ALTER TABLE web3_nonce_slots VALIDATE CONSTRAINT fk_web3_nonce_slots_active_tx;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_web3_nonce_slots_consumed_tx'
          AND conrelid = 'web3_nonce_slots'::regclass
          AND NOT convalidated
    ) THEN
        ALTER TABLE web3_nonce_slots VALIDATE CONSTRAINT fk_web3_nonce_slots_consumed_tx;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_web3_nonce_slots_released_tx'
          AND conrelid = 'web3_nonce_slots'::regclass
          AND NOT convalidated
    ) THEN
        ALTER TABLE web3_nonce_slots VALIDATE CONSTRAINT fk_web3_nonce_slots_released_tx;
    END IF;
END $$;
