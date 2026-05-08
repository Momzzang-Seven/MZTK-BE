-- V065: Add booking-time snapshot columns to class_reservations
--
-- Purpose: Denormalise class title and price at the moment of reservation creation so that
--          historical reservation views always display the information that was correct at the
--          time the user booked, regardless of any subsequent updates by the trainer.
--
-- Migration strategy:
--   - booked_price_amount defaults to 0 for legacy rows (pre-snapshot). The application
--     treats 0 as a signal to fall back to a live cross-module lookup.
--   - booked_class_title defaults to NULL for legacy rows; application falls back similarly.

ALTER TABLE class_reservations
    ADD COLUMN booked_price_amount INT NOT NULL DEFAULT 0
        COMMENT 'Class price in KRW at the time of booking; 0 = legacy record (no snapshot)',
    ADD COLUMN booked_class_title  VARCHAR(100) NULL
        COMMENT 'Class title at the time of booking; NULL = legacy record (no snapshot)';
