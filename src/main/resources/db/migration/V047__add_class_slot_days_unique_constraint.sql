-- V046__add_class_slot_days_unique_constraint.sql
-- Adds a UNIQUE constraint on (slot_id, day_of_week) in class_slot_days.
--
-- Rationale:
--   ClassSlotEntity uses @ElementCollection, which deletes all rows for a slot
--   and re-inserts them on every update. So duplicates cannot occur in practice
--   via the JPA path. However, without a DDL-level constraint, a direct INSERT
--   (e.g., a data migration, admin script, or future bug) could silently create
--   duplicate rows that would be invisible to Hibernate but corrupt biz logic
--   (e.g., a slot appearing twice in the "available days" list).
--
-- Safe to apply on an existing database: the current @ElementCollection strategy
-- guarantees no duplicates already exist, so this constraint will apply cleanly.

ALTER TABLE class_slot_days
    ADD CONSTRAINT uk_class_slot_days UNIQUE (slot_id, day_of_week);
