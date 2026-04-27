CREATE INDEX IF NOT EXISTS idx_comments_writer_created_id_post_active
    ON comments (writer_id, created_at DESC, id DESC, post_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_comments_writer_post_created_id_active
    ON comments (writer_id, post_id, created_at DESC, id DESC)
    WHERE is_deleted = false;
