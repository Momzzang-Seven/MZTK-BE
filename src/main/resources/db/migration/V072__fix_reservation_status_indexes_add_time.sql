-- V072: Rebuild V070 status-filter indexes to include reservation_time between date and id.
--
-- ────────────────────────────────────────────────────────────────────────────
-- Problem (identified in V070 review)
-- ────────────────────────────────────────────────────────────────────────────
-- V070 created status-filter covering indexes with the key order:
--
--   (user_id,    status, reservation_date DESC,              id DESC)
--   (trainer_id, status, reservation_date DESC,              id DESC)
--
-- The cursor queries sort by (reservation_date DESC, reservation_time DESC, id DESC).
-- Because reservation_time is missing from the index, the optimizer must perform a
-- filesort over the date-filtered result set to produce the correct time-descending
-- order within the same date.  On a table with many reservations on the same date
-- this can be expensive, and the index does not cover the cursor boundary predicate
-- on reservation_time.
--
-- ────────────────────────────────────────────────────────────────────────────
-- Solution
-- ────────────────────────────────────────────────────────────────────────────
-- Drop the V070 indexes and recreate them with reservation_time inserted
-- between reservation_date and id, matching the actual ORDER BY contract:
--
--   (user_id,    status, reservation_date DESC, reservation_time DESC, id DESC)
--   (trainer_id, status, reservation_date DESC, reservation_time DESC, id DESC)
--
-- The no-status full-scan indexes (idx_reservations_user_date_time,
-- idx_reservations_trainer_date_time) were created separately and already include
-- reservation_time; they are not affected by this migration.

-- ── User status-filter index ──────────────────────────────────────────────
DROP INDEX idx_reservations_user_status_date ON class_reservations;

CREATE INDEX idx_reservations_user_status_date
    ON class_reservations (user_id, status, reservation_date DESC, reservation_time DESC, id DESC);

-- ── Trainer status-filter index ───────────────────────────────────────────
DROP INDEX idx_reservations_trainer_status_date ON class_reservations;

CREATE INDEX idx_reservations_trainer_status_date
    ON class_reservations (trainer_id, status, reservation_date DESC, reservation_time DESC, id DESC);

-- ────────────────────────────────────────────────────────────────────────────
-- Index summary after V072
-- ────────────────────────────────────────────────────────────────────────────
-- Query path                  | Index used
-- ──────────────────────────────────────────────────────────────────────────────
-- user   + status filter      | idx_reservations_user_status_date    (V072, includes time)
-- user   + no status filter   | idx_reservations_user_date_time      (created elsewhere)
-- trainer + status filter     | idx_reservations_trainer_status_date (V072, includes time)
-- trainer + no status filter  | idx_reservations_trainer_date_time   (created elsewhere)
