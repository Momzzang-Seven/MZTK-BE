CREATE INDEX IF NOT EXISTS idx_web3_qna_answers_post_accepted_created
    ON web3_qna_answers(post_id, accepted, created_at);

CREATE INDEX IF NOT EXISTS idx_web3_qna_questions_state_post
    ON web3_qna_questions(state, post_id);
