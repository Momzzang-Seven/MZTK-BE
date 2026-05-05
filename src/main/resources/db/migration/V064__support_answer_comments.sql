ALTER TABLE comments
    ADD COLUMN target_type varchar(20);

UPDATE comments
SET target_type = 'POST'
WHERE target_type IS NULL;

ALTER TABLE comments
    ALTER COLUMN target_type SET NOT NULL;

ALTER TABLE comments
    ADD COLUMN answer_id bigint;

ALTER TABLE comments
    ALTER COLUMN post_id DROP NOT NULL;

ALTER TABLE comments
    ADD CONSTRAINT chk_comments_target
        CHECK (
            (target_type = 'POST' AND post_id IS NOT NULL AND answer_id IS NULL)
            OR (target_type = 'ANSWER' AND answer_id IS NOT NULL AND post_id IS NULL)
        );

CREATE INDEX idx_comments_answer_cursor
    ON comments (answer_id, created_at ASC, id ASC)
    WHERE parent_id IS NULL AND target_type = 'ANSWER';

CREATE INDEX idx_comments_post_target
    ON comments (target_type, post_id);

CREATE INDEX idx_comments_answer_target
    ON comments (target_type, answer_id);
