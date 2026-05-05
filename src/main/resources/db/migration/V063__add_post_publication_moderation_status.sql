ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS publication_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS current_create_execution_intent_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS publication_failure_terminal_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS publication_failure_reason VARCHAR(500);

ALTER TABLE posts
    ALTER COLUMN publication_status SET DEFAULT 'VISIBLE',
    ALTER COLUMN moderation_status SET DEFAULT 'NORMAL';

WITH latest_question_create_intents AS (
    SELECT DISTINCT ON (e.root_idempotency_key)
        e.root_idempotency_key,
        e.public_id,
        e.status,
        e.last_error_reason
    FROM web3_execution_intents e
    WHERE e.action_type = 'QNA_QUESTION_CREATE'
    ORDER BY e.root_idempotency_key, e.created_at DESC, e.id DESC
),
post_publication_backfill AS (
    SELECT
        p.id AS post_id,
        CASE
            WHEN p.type <> 'QUESTION' THEN 'VISIBLE'
            WHEN q.post_id IS NOT NULL THEN 'VISIBLE'
            WHEN e.status IN ('AWAITING_SIGNATURE', 'SIGNED', 'PENDING_ONCHAIN') THEN 'PENDING'
            WHEN e.status IN ('FAILED_ONCHAIN', 'EXPIRED', 'CANCELED', 'NONCE_STALE') THEN 'FAILED'
            ELSE 'VISIBLE'
        END AS publication_status,
        CASE
            WHEN p.type = 'QUESTION'
                AND q.post_id IS NULL
                AND e.status IN ('AWAITING_SIGNATURE', 'SIGNED', 'PENDING_ONCHAIN')
                THEN e.public_id
            ELSE NULL
        END AS current_create_execution_intent_id,
        CASE
            WHEN p.type = 'QUESTION'
                AND q.post_id IS NULL
                AND e.status IN ('FAILED_ONCHAIN', 'EXPIRED', 'CANCELED', 'NONCE_STALE')
                THEN e.status
            ELSE NULL
        END AS publication_failure_terminal_status,
        CASE
            WHEN p.type = 'QUESTION'
                AND q.post_id IS NULL
                AND e.status IN ('FAILED_ONCHAIN', 'EXPIRED', 'CANCELED', 'NONCE_STALE')
                THEN COALESCE(NULLIF(e.last_error_reason, ''), 'legacy publication backfill')
            ELSE NULL
        END AS publication_failure_reason
    FROM posts p
    LEFT JOIN web3_qna_questions q ON q.post_id = p.id
    LEFT JOIN latest_question_create_intents e
        ON e.root_idempotency_key = CONCAT('qna:qna_question_create:', p.user_id, ':', p.id)
)
UPDATE posts p
SET
    publication_status = b.publication_status,
    current_create_execution_intent_id = b.current_create_execution_intent_id,
    publication_failure_terminal_status = b.publication_failure_terminal_status,
    publication_failure_reason = b.publication_failure_reason
FROM post_publication_backfill b
WHERE p.id = b.post_id
    AND p.publication_status IS NULL;

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

CREATE INDEX IF NOT EXISTS idx_posts_current_create_execution_intent
    ON posts (current_create_execution_intent_id);
