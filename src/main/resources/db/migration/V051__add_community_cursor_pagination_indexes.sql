CREATE INDEX IF NOT EXISTS idx_posts_cursor_created_id
    ON posts (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_posts_type_cursor_created_id
    ON posts (type, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_post_tags_tag_id_post_id
    ON post_tags (tag_id, post_id);

DELETE FROM post_tags kept
USING post_tags duplicate
WHERE kept.post_id = duplicate.post_id
  AND kept.tag_id = duplicate.tag_id
  AND kept.id > duplicate.id;

CREATE UNIQUE INDEX IF NOT EXISTS uk_post_tags_post_id_tag_id
    ON post_tags (post_id, tag_id);

CREATE INDEX IF NOT EXISTS idx_comments_root_cursor
    ON comments (post_id, created_at ASC, id ASC)
    WHERE parent_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_comments_replies_cursor
    ON comments (parent_id, created_at ASC, id ASC);
