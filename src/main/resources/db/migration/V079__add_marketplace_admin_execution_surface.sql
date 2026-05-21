-- MOM-354: marketplace admin execution state surface.

ALTER TABLE class_reservations
    ADD COLUMN IF NOT EXISTS resolved_by VARCHAR(30),
    ADD COLUMN IF NOT EXISTS terminal_reason_code VARCHAR(80);

ALTER TABLE class_reservations
    DROP CONSTRAINT IF EXISTS chk_class_reservations_status,
    DROP CONSTRAINT IF EXISTS chk_class_reservations_escrow_status,
    DROP CONSTRAINT IF EXISTS chk_class_reservations_pending_action,
    DROP CONSTRAINT IF EXISTS chk_class_reservations_resolved_by;

ALTER TABLE class_reservations
    ADD CONSTRAINT chk_class_reservations_status CHECK (
        status IN (
            'HOLDING', 'PENDING', 'APPROVED',
            'USER_CANCELLED', 'REJECTED', 'TIMEOUT_CANCELLED',
            'SETTLED', 'AUTO_SETTLED',
            'PURCHASE_PREPARING', 'PURCHASE_PENDING',
            'CANCEL_PENDING', 'REJECT_PENDING', 'CONFIRM_PENDING',
            'DEADLINE_REFUND_PENDING', 'ADMIN_REFUND_PENDING', 'ADMIN_SETTLE_PENDING',
            'DEADLINE_RECOVERY_REQUIRED',
            'DEADLINE_SYNC_REQUIRED', 'DEADLINE_REFUND_AVAILABLE',
            'MANUAL_SYNC_REQUIRED', 'HOLD_EXPIRED', 'PAYMENT_FAILED',
            'DEADLINE_REFUNDED'
        )
    ) NOT VALID,
    ADD CONSTRAINT chk_class_reservations_escrow_status CHECK (
        escrow_status IS NULL OR escrow_status IN (
            'NONE', 'PURCHASE_PREPARING', 'PURCHASE_PENDING', 'LOCKED',
            'CANCEL_PENDING', 'REJECT_PENDING', 'CONFIRM_PENDING',
            'ADMIN_REFUND_PENDING', 'ADMIN_SETTLE_PENDING',
            'DEADLINE_REFUND_AVAILABLE', 'DEADLINE_REFUND_PENDING',
            'REFUNDED', 'SETTLED', 'DEADLINE_REFUNDED',
            'DEADLINE_RECOVERY_REQUIRED', 'DEADLINE_SYNC_REQUIRED',
            'MANUAL_SYNC_REQUIRED', 'HOLD_EXPIRED', 'PAYMENT_FAILED', 'FAILED'
        )
    ) NOT VALID,
    ADD CONSTRAINT chk_class_reservations_pending_action CHECK (
        pending_action IS NULL OR pending_action IN (
            'PURCHASE', 'BUYER_CANCEL', 'TRAINER_REJECT', 'BUYER_CONFIRM', 'DEADLINE_REFUND',
            'ADMIN_REFUND', 'ADMIN_SETTLE'
        )
    ) NOT VALID,
    ADD CONSTRAINT chk_class_reservations_resolved_by CHECK (
        resolved_by IS NULL OR resolved_by IN ('ADMIN', 'SCHEDULER', 'CHAIN_SYNC')
    ) NOT VALID;

ALTER TABLE class_reservations
    VALIDATE CONSTRAINT chk_class_reservations_status,
    VALIDATE CONSTRAINT chk_class_reservations_escrow_status,
    VALIDATE CONSTRAINT chk_class_reservations_pending_action,
    VALIDATE CONSTRAINT chk_class_reservations_resolved_by;

ALTER TABLE marketplace_reservation_escrows
    DROP CONSTRAINT IF EXISTS chk_marketplace_reservation_escrows_status;

ALTER TABLE marketplace_reservation_escrows
    ADD CONSTRAINT chk_marketplace_reservation_escrows_status CHECK (
        escrow_status IN (
            'NONE',
            'PURCHASE_PREPARING', 'PURCHASE_PENDING',
            'LOCKED',
            'CANCEL_PENDING', 'REJECT_PENDING', 'CONFIRM_PENDING',
            'ADMIN_REFUND_PENDING', 'ADMIN_SETTLE_PENDING',
            'DEADLINE_REFUND_AVAILABLE', 'DEADLINE_REFUND_PENDING',
            'REFUNDED', 'SETTLED', 'DEADLINE_REFUNDED',
            'DEADLINE_RECOVERY_REQUIRED', 'DEADLINE_SYNC_REQUIRED',
            'MANUAL_SYNC_REQUIRED', 'HOLD_EXPIRED', 'PAYMENT_FAILED', 'FAILED'
        )
    );

ALTER TABLE marketplace_reservation_action_states
    ADD COLUMN IF NOT EXISTS request_source VARCHAR(30),
    ADD COLUMN IF NOT EXISTS reason_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS memo VARCHAR(500);

UPDATE marketplace_reservation_action_states
SET request_source = 'USER'
WHERE request_source IS NULL;

ALTER TABLE marketplace_reservation_action_states
    ALTER COLUMN request_source SET NOT NULL,
    ALTER COLUMN actor_user_id DROP NOT NULL;

ALTER TABLE marketplace_reservation_action_states
    DROP CONSTRAINT IF EXISTS chk_marketplace_reservation_action_states_action_type,
    DROP CONSTRAINT IF EXISTS chk_marketplace_reservation_action_states_actor_type,
    DROP CONSTRAINT IF EXISTS chk_marketplace_reservation_action_states_request_source,
    DROP CONSTRAINT IF EXISTS chk_marketplace_reservation_action_states_reason_code,
    DROP CONSTRAINT IF EXISTS chk_marketplace_reservation_action_states_reason_code_required,
    DROP CONSTRAINT IF EXISTS chk_marketplace_reservation_action_states_actor_source;

ALTER TABLE marketplace_reservation_action_states
    ADD CONSTRAINT chk_marketplace_reservation_action_states_action_type CHECK (
        action_type IN (
            'PURCHASE', 'BUYER_CANCEL', 'TRAINER_REJECT', 'BUYER_CONFIRM', 'DEADLINE_REFUND',
            'ADMIN_REFUND', 'ADMIN_SETTLE'
        )
    ),
    ADD CONSTRAINT chk_marketplace_reservation_action_states_actor_type CHECK (
        actor_type IN ('BUYER', 'TRAINER', 'ADMIN', 'SYSTEM')
    ),
    ADD CONSTRAINT chk_marketplace_reservation_action_states_request_source CHECK (
        request_source IN ('USER', 'MANUAL_ADMIN', 'SCHEDULER')
    ),
    ADD CONSTRAINT chk_marketplace_reservation_action_states_reason_code CHECK (
        reason_code IS NULL OR reason_code IN (
            'TRAINER_TIMEOUT', 'SESSION_START_WINDOW_TIMEOUT', 'ADMIN_MANUAL_REFUND',
            'BUYER_CONFIRMATION_TIMEOUT', 'ADMIN_MANUAL_SETTLE'
        )
    ),
    ADD CONSTRAINT chk_marketplace_reservation_action_states_reason_code_required CHECK (
        (
            action_type = 'ADMIN_REFUND'
            AND reason_code IN (
                'TRAINER_TIMEOUT', 'SESSION_START_WINDOW_TIMEOUT', 'ADMIN_MANUAL_REFUND'
            )
        )
        OR (
            action_type = 'ADMIN_SETTLE'
            AND reason_code IN ('BUYER_CONFIRMATION_TIMEOUT', 'ADMIN_MANUAL_SETTLE')
        )
        OR (action_type NOT IN ('ADMIN_REFUND', 'ADMIN_SETTLE') AND reason_code IS NULL)
    ),
    ADD CONSTRAINT chk_marketplace_reservation_action_states_actor_source CHECK (
        (
            request_source = 'USER'
            AND actor_type IN ('BUYER', 'TRAINER')
            AND actor_user_id IS NOT NULL
        )
        OR (
            request_source = 'MANUAL_ADMIN'
            AND actor_type = 'ADMIN'
            AND actor_user_id IS NOT NULL
        )
        OR (
            request_source = 'SCHEDULER'
            AND actor_type = 'SYSTEM'
            AND actor_user_id IS NULL
        )
    );
