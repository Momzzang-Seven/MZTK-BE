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
            'PENDING', 'APPROVED',
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

CREATE TABLE reservation_create_idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    buyer_id BIGINT NOT NULL,
    key_hash VARCHAR(128) NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reservation_id BIGINT REFERENCES class_reservations(id),
    current_execution_intent_public_id VARCHAR(36),
    response_snapshot_json TEXT,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_reservation_create_idempotency_buyer_key UNIQUE (buyer_id, key_hash),
    CONSTRAINT chk_reservation_create_idempotency_status CHECK (
        status IN ('PREPARING', 'INTENT_CREATED', 'BOUND', 'COMPLETED', 'FAILED')
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
