-- V050__add_rejection_reason_to_class_reservations.sql
-- Adds rejection_reason to class_reservations table to store trainer's reason for rejecting a pending reservation.

ALTER TABLE class_reservations
    ADD COLUMN rejection_reason VARCHAR(500);
