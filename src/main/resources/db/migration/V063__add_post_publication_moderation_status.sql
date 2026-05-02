ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS publication_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(20);

ALTER TABLE posts
    ALTER COLUMN publication_status SET DEFAULT 'VISIBLE',
    ALTER COLUMN moderation_status SET DEFAULT 'NORMAL';

UPDATE posts p
SET publication_status = CASE
    WHEN p.type = 'QUESTION'
        AND EXISTS (
            SELECT 1
            FROM web3_qna_questions q
            WHERE q.post_id = p.id
        )
        THEN 'VISIBLE'
    WHEN p.type = 'QUESTION' THEN 'PENDING'
    ELSE 'VISIBLE'
END
WHERE p.publication_status IS NULL;

UPDATE posts
SET moderation_status = 'NORMAL'
WHERE moderation_status IS NULL;

ALTER TABLE posts
    ALTER COLUMN publication_status SET NOT NULL,
    ALTER COLUMN moderation_status SET NOT NULL;

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_publication_status
        CHECK (publication_status IN ('PENDING', 'VISIBLE', 'FAILED'));

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_moderation_status
        CHECK (moderation_status IN ('NORMAL', 'BLOCKED'));

CREATE INDEX IF NOT EXISTS idx_posts_public_cursor_created_id
    ON posts (publication_status, moderation_status, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_posts_public_type_cursor_created_id
    ON posts (publication_status, moderation_status, type, created_at DESC, id DESC);
