CREATE INDEX IF NOT EXISTS idx_post_like_user_target_cursor
    ON post_like (user_id, created_at DESC, id DESC, target_id)
    WHERE target_type = 'POST';
