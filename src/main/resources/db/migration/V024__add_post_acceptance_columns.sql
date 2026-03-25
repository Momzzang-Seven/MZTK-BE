ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS accepted_answer_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

UPDATE posts
SET status = CASE
    WHEN is_solved = TRUE THEN 'RESOLVED'
    ELSE 'OPEN'
END
WHERE status IS NULL;

ALTER TABLE posts
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_status
        CHECK (status IN ('OPEN', 'RESOLVED'));

ALTER TABLE posts
    ADD CONSTRAINT fk_posts_accepted_answer
        FOREIGN KEY (accepted_answer_id) REFERENCES answers(id);
