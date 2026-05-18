-- MOM-313: marketplace user-managed EIP-7702 escrow foundation.
-- This migration only adds user-flow state storage and guards. It does not add scheduler/admin
-- actions or reconciliation jobs.

-- Preflight: duplicate active reservation rows would break the new natural guard.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM class_reservations
        WHERE status NOT IN (
            'USER_CANCELLED', 'REJECTED', 'TIMEOUT_CANCELLED',
            'SETTLED', 'AUTO_SETTLED'
        )
        GROUP BY user_id, class_slot_id, reservation_date, reservation_time
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate active class_reservations must be repaired before V074';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM class_reservations
        WHERE status IN ('PENDING', 'APPROVED')
          AND tx_hash = 'ESCROW_DISPATCH_PENDING'
    ) THEN
        RAISE EXCEPTION 'Active ESCROW_DISPATCH_PENDING rows require manual repair before V074';
    END IF;
END $$;

ALTER TABLE class_reservations
    ADD COLUMN escrow_status VARCHAR(40),
    ADD COLUMN escrow_flow VARCHAR(30) DEFAULT 'LEGACY_DISPATCH',
    ADD COLUMN order_key VARCHAR(66),
    ADD COLUMN current_execution_intent_public_id VARCHAR(36),
    ADD COLUMN buyer_wallet_address VARCHAR(42),
    ADD COLUMN trainer_wallet_address VARCHAR(42),
    ADD COLUMN token_address VARCHAR(42),
    ADD COLUMN price_base_units VARCHAR(100),
    ADD COLUMN hold_expires_at TIMESTAMP,
    ADD COLUMN pending_action_expires_at TIMESTAMP,
    ADD COLUMN expected_contract_deadline_epoch_seconds BIGINT,
    ADD COLUMN expected_contract_deadline_at TIMESTAMP,
    ADD COLUMN contract_deadline_epoch_seconds BIGINT,
    ADD COLUMN contract_deadline_at TIMESTAMP,
    ADD COLUMN pending_action VARCHAR(40),
    ADD COLUMN pending_attempt_token VARCHAR(100),
    ADD COLUMN pending_expected_version BIGINT,
    ADD COLUMN pending_expected_status VARCHAR(30),
    ADD COLUMN pending_expected_escrow_status VARCHAR(40),
    ADD COLUMN prior_status VARCHAR(30),
    ADD COLUMN prior_escrow_status VARCHAR(40),
    ADD COLUMN create_idempotency_key_hash VARCHAR(128),
    ADD COLUMN create_payload_hash VARCHAR(128),
    ADD COLUMN server_signature_signed_at TIMESTAMP,
    ADD COLUMN server_signature_expires_at TIMESTAMP,
    ADD COLUMN escrow_failure_code VARCHAR(100),
    ADD COLUMN escrow_failure_message VARCHAR(500);

UPDATE class_reservations
SET escrow_flow = 'LEGACY_DISPATCH'
WHERE escrow_flow IS NULL;

ALTER TABLE class_reservations
    DROP CONSTRAINT IF EXISTS chk_class_reservations_status;

ALTER TABLE class_reservations
    ADD CONSTRAINT chk_class_reservations_status CHECK (
        status IN (
            'HOLDING', 'PENDING', 'APPROVED',
            'USER_CANCELLED', 'REJECTED', 'TIMEOUT_CANCELLED',
            'SETTLED', 'AUTO_SETTLED',
            'PURCHASE_PREPARING', 'PURCHASE_PENDING',
            'CANCEL_PENDING', 'REJECT_PENDING', 'CONFIRM_PENDING',
            'DEADLINE_REFUND_PENDING', 'DEADLINE_RECOVERY_REQUIRED',
            'DEADLINE_SYNC_REQUIRED', 'DEADLINE_REFUND_AVAILABLE',
            'MANUAL_SYNC_REQUIRED', 'HOLD_EXPIRED', 'PAYMENT_FAILED',
            'DEADLINE_REFUNDED'
        )
    ),
    ADD CONSTRAINT chk_class_reservations_escrow_status CHECK (
        escrow_status IS NULL OR escrow_status IN (
            'NONE', 'PURCHASE_PREPARING', 'PURCHASE_PENDING', 'LOCKED',
            'CANCEL_PENDING', 'REJECT_PENDING', 'CONFIRM_PENDING',
            'DEADLINE_REFUND_AVAILABLE', 'DEADLINE_REFUND_PENDING',
            'REFUNDED', 'SETTLED', 'DEADLINE_REFUNDED',
            'DEADLINE_RECOVERY_REQUIRED', 'DEADLINE_SYNC_REQUIRED',
            'MANUAL_SYNC_REQUIRED', 'HOLD_EXPIRED', 'PAYMENT_FAILED', 'FAILED'
        )
    ),
    ADD CONSTRAINT chk_class_reservations_escrow_flow CHECK (
        escrow_flow IS NULL OR escrow_flow IN ('LEGACY_DISPATCH', 'USER_EIP7702')
    ),
    ADD CONSTRAINT chk_class_reservations_order_key CHECK (
        order_key IS NULL OR order_key ~ '^0x[0-9a-f]{64}$'
    ),
    ADD CONSTRAINT chk_class_reservations_contract_deadline_pair CHECK (
        (contract_deadline_epoch_seconds IS NULL AND contract_deadline_at IS NULL)
        OR (contract_deadline_epoch_seconds IS NOT NULL AND contract_deadline_at IS NOT NULL
            AND contract_deadline_epoch_seconds >= 0)
    ),
    ADD CONSTRAINT chk_class_reservations_expected_deadline_pair CHECK (
        (expected_contract_deadline_epoch_seconds IS NULL AND expected_contract_deadline_at IS NULL)
        OR (expected_contract_deadline_epoch_seconds IS NOT NULL
            AND expected_contract_deadline_at IS NOT NULL
            AND expected_contract_deadline_epoch_seconds >= 0)
    ),
    ADD CONSTRAINT chk_class_reservations_pending_action CHECK (
        pending_action IS NULL OR pending_action IN (
            'PURCHASE', 'BUYER_CANCEL', 'TRAINER_REJECT', 'BUYER_CONFIRM', 'DEADLINE_REFUND'
        )
    );

CREATE UNIQUE INDEX uk_class_reservations_order_key
    ON class_reservations (order_key)
    WHERE order_key IS NOT NULL;

CREATE UNIQUE INDEX uk_class_reservations_current_execution_intent_public_id
    ON class_reservations (current_execution_intent_public_id)
    WHERE current_execution_intent_public_id IS NOT NULL;

CREATE INDEX idx_class_reservations_escrow_flow_status
    ON class_reservations (escrow_flow, status);

CREATE UNIQUE INDEX uk_class_reservations_active_buyer_slot_datetime
    ON class_reservations (user_id, class_slot_id, reservation_date, reservation_time)
    WHERE status NOT IN (
        'USER_CANCELLED', 'REJECTED', 'TIMEOUT_CANCELLED',
        'SETTLED', 'AUTO_SETTLED', 'HOLD_EXPIRED',
        'PAYMENT_FAILED', 'DEADLINE_REFUNDED'
    );

CREATE TABLE reservation_slot_date_locks (
    id BIGSERIAL PRIMARY KEY,
    class_slot_id BIGINT NOT NULL REFERENCES class_slots(id),
    reservation_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_reservation_slot_date_locks_slot_date
        UNIQUE (class_slot_id, reservation_date)
);

CREATE TABLE marketplace_reservation_escrows (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL UNIQUE REFERENCES class_reservations(id) ON DELETE CASCADE,
    escrow_flow VARCHAR(30) NOT NULL,
    escrow_status VARCHAR(40) NOT NULL,
    order_key VARCHAR(66) UNIQUE,
    buyer_wallet_address VARCHAR(42),
    trainer_wallet_address VARCHAR(42),
    token_address VARCHAR(42),
    price_base_units NUMERIC(78, 0),
    hold_expires_at TIMESTAMP,
    expected_contract_deadline_epoch_seconds BIGINT,
    expected_contract_deadline_at TIMESTAMP,
    contract_deadline_epoch_seconds BIGINT,
    contract_deadline_at TIMESTAMP,
    last_chain_state INTEGER,
    last_chain_synced_at TIMESTAMP,
    last_tx_hash VARCHAR(66),
    last_failure_code VARCHAR(120),
    last_failure_message VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_marketplace_reservation_escrows_id_reservation
        UNIQUE (id, reservation_id),
    CONSTRAINT chk_marketplace_reservation_escrows_flow CHECK (
        escrow_flow IN ('LEGACY_DISPATCH', 'USER_EIP7702')
    ),
    CONSTRAINT chk_marketplace_reservation_escrows_status CHECK (
        escrow_status IN (
            'NONE', 'LOCKED',
            'DEADLINE_REFUND_AVAILABLE',
            'REFUNDED', 'SETTLED', 'DEADLINE_REFUNDED',
            'DEADLINE_RECOVERY_REQUIRED', 'DEADLINE_SYNC_REQUIRED',
            'MANUAL_SYNC_REQUIRED', 'HOLD_EXPIRED', 'PAYMENT_FAILED', 'FAILED'
        )
    ),
    CONSTRAINT chk_marketplace_reservation_escrows_order_key CHECK (
        order_key IS NULL OR order_key ~ '^0x[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_marketplace_reservation_escrows_address_format CHECK (
        (buyer_wallet_address IS NULL OR buyer_wallet_address ~ '^0x[0-9a-fA-F]{40}$')
        AND (trainer_wallet_address IS NULL OR trainer_wallet_address ~ '^0x[0-9a-fA-F]{40}$')
        AND (token_address IS NULL OR token_address ~ '^0x[0-9a-fA-F]{40}$')
    ),
    CONSTRAINT chk_marketplace_reservation_escrows_price CHECK (
        price_base_units IS NULL OR (
            price_base_units > 0
            AND price_base_units <= 115792089237316195423570985008687907853269984665640564039457584007913129639935
        )
    ),
    CONSTRAINT chk_marketplace_reservation_escrows_expected_deadline_pair CHECK (
        (expected_contract_deadline_epoch_seconds IS NULL AND expected_contract_deadline_at IS NULL)
        OR (expected_contract_deadline_epoch_seconds IS NOT NULL
            AND expected_contract_deadline_at IS NOT NULL
            AND expected_contract_deadline_epoch_seconds >= 0)
    ),
    CONSTRAINT chk_marketplace_reservation_escrows_contract_deadline_pair CHECK (
        (contract_deadline_epoch_seconds IS NULL AND contract_deadline_at IS NULL)
        OR (contract_deadline_epoch_seconds IS NOT NULL
            AND contract_deadline_at IS NOT NULL
            AND contract_deadline_epoch_seconds >= 0)
    )
);

CREATE INDEX idx_marketplace_reservation_escrows_flow_status
    ON marketplace_reservation_escrows (escrow_flow, escrow_status);

CREATE INDEX idx_marketplace_reservation_escrows_hold_expires_at
    ON marketplace_reservation_escrows (hold_expires_at)
    WHERE hold_expires_at IS NOT NULL;

CREATE TABLE marketplace_reservation_action_states (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES class_reservations(id) ON DELETE CASCADE,
    escrow_id BIGINT NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    attempt_no INTEGER NOT NULL,
    attempt_token VARCHAR(100) NOT NULL UNIQUE,
    execution_intent_public_id VARCHAR(36) UNIQUE,
    root_idempotency_key VARCHAR(250),
    payload_hash VARCHAR(66),
    status VARCHAR(40) NOT NULL,
    expected_reservation_version BIGINT,
    expected_reservation_status VARCHAR(30),
    expected_escrow_status VARCHAR(40),
    prior_reservation_status VARCHAR(30),
    prior_escrow_status VARCHAR(40),
    preparation_expires_at TIMESTAMP,
    server_signature_signed_at TIMESTAMP,
    server_signature_expires_at TIMESTAMP,
    action_reason VARCHAR(500),
    retryable BOOLEAN,
    error_code VARCHAR(120),
    error_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_marketplace_reservation_action_states_attempt
        UNIQUE (reservation_id, attempt_no),
    CONSTRAINT uk_marketplace_reservation_action_states_graph
        UNIQUE (id, reservation_id, escrow_id),
    CONSTRAINT fk_marketplace_reservation_action_states_escrow_graph
        FOREIGN KEY (escrow_id, reservation_id)
        REFERENCES marketplace_reservation_escrows(id, reservation_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_marketplace_reservation_action_states_attempt_no CHECK (attempt_no > 0),
    CONSTRAINT chk_marketplace_reservation_action_states_action_type CHECK (
        action_type IN ('PURCHASE', 'BUYER_CANCEL', 'TRAINER_REJECT', 'BUYER_CONFIRM', 'DEADLINE_REFUND')
    ),
    CONSTRAINT chk_marketplace_reservation_action_states_actor_type CHECK (
        actor_type IN ('BUYER', 'TRAINER', 'SYSTEM')
    ),
    CONSTRAINT chk_marketplace_reservation_action_states_status CHECK (
        status IN (
            'PREPARING', 'INTENT_BOUND', 'PREPARATION_FAILED',
            'CONFIRMED', 'TERMINATED', 'ROLLED_BACK', 'STALE'
        )
    ),
    CONSTRAINT chk_marketplace_reservation_action_states_payload_hash CHECK (
        payload_hash IS NULL OR payload_hash ~ '^0x[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_marketplace_reservation_action_states_bound_intent CHECK (
        status IN ('PREPARING', 'PREPARATION_FAILED')
        OR execution_intent_public_id IS NOT NULL
    )
);

CREATE UNIQUE INDEX uk_marketplace_reservation_action_states_active
    ON marketplace_reservation_action_states (reservation_id)
    WHERE status IN ('PREPARING', 'INTENT_BOUND');

CREATE INDEX idx_marketplace_reservation_action_states_reservation_latest
    ON marketplace_reservation_action_states (reservation_id, attempt_no DESC, id DESC);

CREATE INDEX idx_marketplace_reservation_action_states_reservation_status_latest
    ON marketplace_reservation_action_states (reservation_id, status, attempt_no DESC, id DESC);

CREATE INDEX idx_marketplace_reservation_action_states_reservation_action_latest
    ON marketplace_reservation_action_states (reservation_id, action_type, attempt_no DESC, id DESC);

CREATE INDEX idx_marketplace_reservation_action_states_action_status_updated
    ON marketplace_reservation_action_states (action_type, status, updated_at);

CREATE INDEX idx_marketplace_reservation_action_states_root_status_latest
    ON marketplace_reservation_action_states (root_idempotency_key, status, attempt_no DESC)
    WHERE root_idempotency_key IS NOT NULL;

CREATE TABLE reservation_create_idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    buyer_id BIGINT NOT NULL,
    key_hash VARCHAR(128) NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reservation_id BIGINT REFERENCES class_reservations(id),
    escrow_id BIGINT,
    action_state_id BIGINT,
    response_snapshot_json TEXT,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_reservation_create_idempotency_buyer_key UNIQUE (buyer_id, key_hash),
    CONSTRAINT fk_reservation_create_idempotency_escrow_graph
        FOREIGN KEY (escrow_id, reservation_id)
        REFERENCES marketplace_reservation_escrows(id, reservation_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_reservation_create_idempotency_action_graph
        FOREIGN KEY (action_state_id, reservation_id, escrow_id)
        REFERENCES marketplace_reservation_action_states(id, reservation_id, escrow_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_reservation_create_idempotency_status CHECK (
        status IN ('PREPARING', 'BOUND', 'COMPLETED', 'FAILED')
    ),
    CONSTRAINT chk_reservation_create_idempotency_graph CHECK (
        status NOT IN ('BOUND', 'COMPLETED')
        OR (reservation_id IS NOT NULL AND escrow_id IS NOT NULL AND action_state_id IS NOT NULL)
    )
);

CREATE INDEX idx_reservation_create_idempotency_reservation_id
    ON reservation_create_idempotency_keys (reservation_id);

CREATE INDEX idx_reservation_create_idempotency_expires_at
    ON reservation_create_idempotency_keys (status, expires_at);

ALTER TABLE trainer_strike_records
    ADD COLUMN source_type VARCHAR(80),
    ADD COLUMN source_id VARCHAR(120);

CREATE UNIQUE INDEX uk_trainer_strike_records_source
    ON trainer_strike_records (source_type, source_id)
    WHERE source_type IS NOT NULL AND source_id IS NOT NULL;
