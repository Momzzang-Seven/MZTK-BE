CREATE TABLE IF NOT EXISTS web3_qna_questions (
    post_id            BIGINT PRIMARY KEY,
    question_id        VARCHAR(66) NOT NULL,
    asker_user_id      BIGINT NOT NULL,
    token_address      VARCHAR(42) NOT NULL,
    reward_amount_wei  NUMERIC(78, 0) NOT NULL,
    question_hash      VARCHAR(66) NOT NULL,
    accepted_answer_id VARCHAR(66) NOT NULL DEFAULT '0x0000000000000000000000000000000000000000000000000000000000000000',
    answer_count       INTEGER NOT NULL DEFAULT 0,
    state              INTEGER NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_web3_qna_questions_question_id UNIQUE (question_id),
    CONSTRAINT ck_web3_qna_questions_reward_positive CHECK (reward_amount_wei > 0),
    CONSTRAINT ck_web3_qna_questions_answer_count_non_negative CHECK (answer_count >= 0),
    CONSTRAINT ck_web3_qna_questions_state
        CHECK (state IN (1000, 1100, 2100, 4000, 5000, 5100))
);

CREATE INDEX IF NOT EXISTS idx_web3_qna_questions_state
    ON web3_qna_questions(state);

CREATE TABLE IF NOT EXISTS web3_qna_answers (
    answer_id          BIGINT PRIMARY KEY,
    post_id            BIGINT NOT NULL,
    question_id        VARCHAR(66) NOT NULL,
    answer_key         VARCHAR(66) NOT NULL,
    responder_user_id  BIGINT NOT NULL,
    content_hash       VARCHAR(66) NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_web3_qna_answers_answer_key UNIQUE (answer_key)
);

CREATE INDEX IF NOT EXISTS idx_web3_qna_answers_post_id
    ON web3_qna_answers(post_id);

-- QnA reward execution now uses shared execution intents + escrow projections only.
DROP TABLE IF EXISTS web3_transfer_prepares;
