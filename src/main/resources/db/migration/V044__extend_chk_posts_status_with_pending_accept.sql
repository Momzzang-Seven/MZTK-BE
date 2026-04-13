ALTER TABLE posts
    DROP CONSTRAINT IF EXISTS chk_posts_status;

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_status
        CHECK (status IN ('OPEN', 'PENDING_ACCEPT', 'RESOLVED'));
