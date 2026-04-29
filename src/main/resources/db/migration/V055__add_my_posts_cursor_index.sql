CREATE INDEX IF NOT EXISTS idx_posts_user_type_cursor
    ON posts (user_id, type, created_at DESC, id DESC);
