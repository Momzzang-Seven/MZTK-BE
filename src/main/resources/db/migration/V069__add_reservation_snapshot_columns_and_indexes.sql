-- V065: Add booking-time snapshot columns and covering indexes for reservation list queries
--
-- ────────────────────────────────────────────────────────────────────────────
-- Part 1 — Snapshot columns
-- ────────────────────────────────────────────────────────────────────────────
-- Purpose: Denormalise class title and price at the moment of reservation creation so that
--          historical reservation views always display the information that was correct at the
--          time the user booked, regardless of any subsequent updates by the trainer.
--
-- Migration strategy:
--   - booked_price_amount is NULL for legacy rows (pre-snapshot). The application treats NULL
--     as a signal to fall back to a live cross-module lookup.
--   - booked_class_title is NULL for legacy rows; application falls back similarly.

ALTER TABLE class_reservations
    ADD COLUMN booked_price_amount INT NULL
        COMMENT 'Class price in KRW at the time of booking; NULL = legacy record (no snapshot)',
    ADD COLUMN booked_class_title  VARCHAR(100) NULL
        COMMENT 'Class title at the time of booking; NULL = legacy record (no snapshot)';

-- ────────────────────────────────────────────────────────────────────────────
-- Part 2 — Composite covering indexes for cursor-paginated reservation lists
-- ────────────────────────────────────────────────────────────────────────────
-- Problem: GetUserReservationsService and GetTrainerReservationsService filter by
--   (userId / trainerId) and optional status, then ORDER BY reservationDate DESC, id DESC.
--   With only single-column indexes on user_id, trainer_id, status, MySQL/MariaDB must perform a
--   filesort over all matching rows after the index scan. As reservation counts grow this becomes
--   progressively more expensive.
--
-- Solution: two covering composite indexes that allow the optimizer to satisfy both the equality
--   filter AND the cursor sort from a single index range scan.
--
--   idx_reservations_user_status_date  -- used by GetUserReservationsService
--     (user_id, status, reservation_date DESC, id DESC)
--
--   idx_reservations_trainer_status_date  -- used by GetTrainerReservationsService
--     (trainer_id, status, reservation_date DESC, id DESC)
--
-- Note: reservation_date + id is the keyset cursor key (ORDER BY reservationDate DESC, id DESC).
-- Including id in the index removes a separate id-lookup step on the keyset boundary rows.

CREATE INDEX idx_reservations_user_status_date
    ON class_reservations (user_id, status, reservation_date DESC, id DESC);

CREATE INDEX idx_reservations_trainer_status_date
    ON class_reservations (trainer_id, status, reservation_date DESC, id DESC);
