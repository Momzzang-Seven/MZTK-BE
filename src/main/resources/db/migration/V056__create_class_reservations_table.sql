-- V049__create_class_reservations_table.sql
-- Creates the class_reservations table for the Marketplace Reservation module.
-- Denormalised columns (trainer_id, duration_minutes) avoid cross-module joins at runtime.

-- ─────────────────────────────────────────────────────────────
-- class_reservations
-- ─────────────────────────────────────────────────────────────
CREATE TABLE class_reservations (
    id               BIGSERIAL    PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    trainer_id       BIGINT       NOT NULL,              -- denormalised from marketplace_classes.trainer_id
    class_slot_id    BIGINT       NOT NULL
                     REFERENCES class_slots(id),
    reservation_date DATE         NOT NULL,              -- actual session date
    reservation_time TIME         NOT NULL,              -- session start time (cross-validated with slot)
    duration_minutes INT          NOT NULL,              -- denormalised from marketplace_classes; used by auto-settle
    status           VARCHAR(30)  NOT NULL,
    user_request     VARCHAR(500),                       -- optional user note
    order_id         VARCHAR(100),                       -- server-generated UUID; escrow contract identifier
    tx_hash          VARCHAR(100),                       -- latest on-chain transaction hash
    version          BIGINT       NOT NULL DEFAULT 0,    -- JPA @Version optimistic lock
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_class_reservations_status CHECK (
        status IN (
            'PENDING', 'APPROVED',
            'USER_CANCELLED', 'REJECTED', 'TIMEOUT_CANCELLED',
            'SETTLED', 'AUTO_SETTLED'
        )
    ),
    CONSTRAINT chk_class_reservations_duration CHECK (duration_minutes > 0)
);

-- Indexes for common query patterns
CREATE INDEX idx_class_reservations_user_id      ON class_reservations (user_id);
CREATE INDEX idx_class_reservations_trainer_id   ON class_reservations (trainer_id);
CREATE INDEX idx_class_reservations_slot_id      ON class_reservations (class_slot_id);
CREATE INDEX idx_class_reservations_status       ON class_reservations (status);
CREATE INDEX idx_class_reservations_created_at   ON class_reservations (created_at);

-- Composite index for auto-cancel scheduler query:
--   WHERE status = 'PENDING' AND (created_at < X OR TIMESTAMP(reservation_date, reservation_time) < Y)
CREATE INDEX idx_class_reservations_pending_cancel
    ON class_reservations (status, created_at, reservation_date, reservation_time);

-- Composite index for auto-settle scheduler query:
--   WHERE status = 'APPROVED' AND TIMESTAMP(...) + duration_minutes + 24h < NOW
CREATE INDEX idx_class_reservations_approved_settle
    ON class_reservations (status, reservation_date, reservation_time, duration_minutes);

-- Unique constraint on order_id to prevent duplicate escrow orders
CREATE UNIQUE INDEX idx_class_reservations_order_id ON class_reservations (order_id)
    WHERE order_id IS NOT NULL;
