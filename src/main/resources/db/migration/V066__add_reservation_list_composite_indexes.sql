-- V066: Add composite indexes for reservation list queries
--
-- Problem: GetUserReservationsService and GetTrainerReservationsService filter by
--   (userId / trainerId) and optional status, then ORDER BY reservationDate DESC, reservationTime DESC.
--   With only single-column indexes on user_id, trainer_id, status, MySQL/MariaDB must perform a
--   filesort over all matching rows after the index scan. As reservation counts grow this becomes
--   progressively more expensive.
--
-- Solution: two covering composite indexes that allow the optimizer to satisfy both the equality
--   filter AND the sort from a single index range scan.
--
--   idx_reservations_user_status_date  -- used by GetUserReservationsService
--     (user_id, status, reservation_date DESC, reservation_time DESC)
--
--   idx_reservations_trainer_status_date  -- used by GetTrainerReservationsService
--     (trainer_id, status, reservation_date DESC, reservation_time DESC)
--
-- The status column is placed second so that an optional status filter can be applied before the
-- date sort. When no status filter is given the optimizer still benefits from the leading user_id /
-- trainer_id column for the primary equality filter and can use the date columns for sorting.

CREATE INDEX idx_reservations_user_status_date
    ON class_reservations (user_id, status, reservation_date DESC, reservation_time DESC);

CREATE INDEX idx_reservations_trainer_status_date
    ON class_reservations (trainer_id, status, reservation_date DESC, reservation_time DESC);
