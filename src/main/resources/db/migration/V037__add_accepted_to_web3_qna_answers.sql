ALTER TABLE web3_qna_answers
    ADD COLUMN accepted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS web3_question_reward_intents (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    accepted_comment_id BIGINT NOT NULL,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    amount_wei NUMERIC(78, 0) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_web3_question_reward_intents_post_id UNIQUE (post_id)
);

CREATE INDEX IF NOT EXISTS idx_web3_question_reward_intents_status
    ON web3_question_reward_intents(status);

ALTER TABLE posts
    DROP CONSTRAINT IF EXISTS chk_posts_status;

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_status
        CHECK (status IN ('OPEN', 'PENDING_ACCEPT', 'RESOLVED'));
