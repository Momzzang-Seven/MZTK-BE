-- V071: Add cursor-pagination indexes for reservation list queries without a status filter
--      Queries sort by (reservation_date DESC, reservation_time DESC, id DESC) to match
--      the session-time ordering contract that users see on the reservation list.
--
-- ────────────────────────────────────────────────────────────────────────────
-- Context
-- ────────────────────────────────────────────────────────────────────────────
-- V070 introduced two composite indexes for cursor-paginated reservation lists:
--
--   idx_reservations_user_status_date    (user_id, status, reservation_date DESC, reservation_time DESC, id DESC)
--   idx_reservations_trainer_status_date (trainer_id, status, reservation_date DESC, reservation_time DESC, id DESC)
--
-- These indexes are optimal when a status filter is provided, but have one gap:
--
-- Status-absent queries: when the caller passes no status filter, the JPQL predicate
-- (:status IS NULL OR r.status = :status) prevents the optimizer from using the
-- status-leading indexes for the sort, falling back to a filesort.
--
-- ────────────────────────────────────────────────────────────────────────────
-- Solution
-- ────────────────────────────────────────────────────────────────────────────
-- Two new covering indexes used by the status-absent cursor paths:
--
--   idx_reservations_user_date_time  -- used by findByUserIdCursorNoStatus
--     (user_id, reservation_date DESC, reservation_time DESC, id DESC)
--
--   idx_reservations_trainer_date_time  -- used by findByTrainerIdCursorNoStatus
--     (trainer_id, reservation_date DESC, reservation_time DESC, id DESC)

CREATE INDEX idx_reservations_user_date_time
    ON class_reservations (user_id, reservation_date DESC, reservation_time DESC, id DESC);

CREATE INDEX idx_reservations_trainer_date_time
    ON class_reservations (trainer_id, reservation_date DESC, reservation_time DESC, id DESC);

-- ────────────────────────────────────────────────────────────────────────────
-- Index summary after V071
-- ────────────────────────────────────────────────────────────────────────────
-- Query path                  | Index used
-- ──────────────────────────────────────────────────────────────────────────────
-- user   + status filter      | idx_reservations_user_status_date    (V070)
-- user   + no status filter   | idx_reservations_user_date_time      (V071)
-- trainer + status filter     | idx_reservations_trainer_status_date (V070)
-- trainer + no status filter  | idx_reservations_trainer_date_time   (V071)
