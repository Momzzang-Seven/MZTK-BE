-- flyway:executeInTransaction=false
-- MOM-458: non-transactional lookup indexes, normalization, and sponsor nonce slot backfill.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT chain_id, LOWER(from_address), nonce
            FROM web3_transactions
            WHERE nonce IS NOT NULL
            GROUP BY chain_id, LOWER(from_address), nonce
            HAVING COUNT(*) > 1
        ) duplicate_nonce_scope
    ) THEN
        RAISE EXCEPTION
            'Duplicate web3 transaction nonce scopes would be created by lower-case normalization';
    END IF;
END $$;

UPDATE web3_transactions
SET from_address = LOWER(from_address),
    to_address = LOWER(to_address),
    authority_address = LOWER(authority_address),
    delegate_target = LOWER(delegate_target)
WHERE from_address <> LOWER(from_address)
   OR to_address <> LOWER(to_address)
   OR (authority_address IS NOT NULL AND authority_address <> LOWER(authority_address))
   OR (delegate_target IS NOT NULL AND delegate_target <> LOWER(delegate_target));

WITH normalized_nonce_state AS (
    SELECT
        LOWER(from_address) AS from_address,
        MAX(next_nonce) AS next_nonce,
        MAX(updated_at) AS updated_at
    FROM web3_nonce_state
    GROUP BY LOWER(from_address)
),
deleted_nonce_state AS (
    DELETE FROM web3_nonce_state
)
INSERT INTO web3_nonce_state(from_address, next_nonce, updated_at)
SELECT from_address, next_nonce, updated_at
FROM normalized_nonce_state;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_index i ON i.indexrelid = c.oid
        WHERE c.oid = to_regclass('idx_web3_tx_sender_nonce')
          AND (NOT i.indisvalid OR NOT i.indisready)
    ) THEN
        DROP INDEX idx_web3_tx_sender_nonce;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_index i ON i.indexrelid = c.oid
        WHERE c.oid = to_regclass('idx_web3_tx_eip7702_authority_nonce')
          AND (NOT i.indisvalid OR NOT i.indisready)
    ) THEN
        DROP INDEX idx_web3_tx_eip7702_authority_nonce;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_web3_tx_id_chain_sender_nonce'
          AND conrelid = 'web3_transactions'::regclass
    ) AND EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_index i ON i.indexrelid = c.oid
        WHERE c.oid = to_regclass('uk_web3_tx_id_chain_sender_nonce')
          AND (NOT i.indisvalid OR NOT i.indisready)
    ) THEN
        DROP INDEX uk_web3_tx_id_chain_sender_nonce;
    END IF;
END $$;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_sender_nonce
    ON web3_transactions(chain_id, from_address, nonce)
    WHERE nonce IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_eip7702_authority_nonce
    ON web3_transactions(authority_address, authorization_nonce)
    WHERE tx_type = 'EIP7702'
      AND authority_address IS NOT NULL
      AND authorization_nonce IS NOT NULL;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_web3_tx_id_chain_sender_nonce
    ON web3_transactions(id, chain_id, from_address, nonce);

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
