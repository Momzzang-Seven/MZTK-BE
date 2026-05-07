CREATE TABLE IF NOT EXISTS qna_question_update_states (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    requester_user_id BIGINT NOT NULL,
    update_version BIGINT NOT NULL,
    update_token VARCHAR(64) NOT NULL,
    expected_question_hash VARCHAR(66) NOT NULL,
    execution_intent_public_id VARCHAR(36),
    status VARCHAR(30) NOT NULL,
    preparation_retryable BOOLEAN NOT NULL DEFAULT TRUE,
    last_error_code VARCHAR(120),
    last_error_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_qna_question_update_states_post_version UNIQUE (post_id, update_version),
    CONSTRAINT uk_qna_question_update_states_token UNIQUE (update_token),
    CONSTRAINT ck_qna_question_update_states_status
        CHECK (status IN ('PREPARING','PREPARATION_FAILED','INTENT_BOUND','CONFIRMED','STALE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_qna_question_update_states_intent_public_id
    ON qna_question_update_states(execution_intent_public_id)
    WHERE execution_intent_public_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_qna_question_update_states_post_latest
    ON qna_question_update_states(post_id, update_version DESC);

CREATE INDEX IF NOT EXISTS idx_qna_question_update_states_status_updated_at
    ON qna_question_update_states(status, updated_at);
