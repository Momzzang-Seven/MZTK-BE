CREATE INDEX IF NOT EXISTS idx_user_progress_leaderboard_order
    ON user_progress (level DESC, lifetime_xp DESC, available_xp DESC, user_id ASC);
