-- V070: Add cursor-pagination indexes for reservation list queries without a status filter
--      Also revises V069 sort order description: queries now sort by
--      (reservation_date DESC, reservation_time DESC, id DESC) to match the session-time
--      ordering contract that users see on the reservation list.
--
-- ────────────────────────────────────────────────────────────────────────────
-- Context
-- ────────────────────────────────────────────────────────────────────────────
-- V069 introduced two composite indexes for cursor-paginated reservation lists:
--
--   idx_reservations_user_status_date    (user_id, status, reservation_date DESC, id DESC)
--   idx_reservations_trainer_status_date (trainer_id, status, reservation_date DESC, id DESC)
--
-- These indexes are optimal when a status filter is provided, but have two gaps:
--
-- (1) Status-absent queries: when the caller passes no status filter, the JPQL predicate
--     (:status IS NULL OR r.status = :status) prevents the optimizer from using the
--     status-leading indexes for the sort, falling back to a filesort.
--
-- (2) Sort regression: V069 indexes were designed for (date, id) ordering, but the
--     cursor queries now sort by (date, time, id) to preserve session-time ordering
--     within the same date — matching the non-paginated findByUserId sort contract.
--
-- ────────────────────────────────────────────────────────────────────────────
-- Solution
-- ────────────────────────────────────────────────────────────────────────────
-- Two new covering indexes that include reservation_time between date and id.
-- These are used by the status-absent cursor paths:
--
--   idx_reservations_user_date_time  -- used by findByUserIdCursorNoStatus
--     (user_id, reservation_date DESC, reservation_time DESC, id DESC)
--
--   idx_reservations_trainer_date_time  -- used by findByTrainerIdCursorNoStatus
--     (trainer_id, reservation_date DESC, reservation_time DESC, id DESC)
--
-- The V069 indexes (with status) still serve the status-present cursor paths but would
-- also benefit from having reservation_time inserted before id. That index rebuild is
-- deferred to a separate migration to keep this change minimal.

CREATE INDEX idx_reservations_user_date_time
    ON class_reservations (user_id, reservation_date DESC, reservation_time DESC, id DESC);

CREATE INDEX idx_reservations_trainer_date_time
    ON class_reservations (trainer_id, reservation_date DESC, reservation_time DESC, id DESC);

-- ────────────────────────────────────────────────────────────────────────────
-- Index summary after V070
-- ────────────────────────────────────────────────────────────────────────────
-- Query path                  | Index used
-- ──────────────────────────────────────────────────────────────────────────────
-- user   + status filter      | idx_reservations_user_status_date    (V069)
-- user   + no status filter   | idx_reservations_user_date_time      (V070)
-- trainer + status filter     | idx_reservations_trainer_status_date (V069)
-- trainer + no status filter  | idx_reservations_trainer_date_time   (V070)
