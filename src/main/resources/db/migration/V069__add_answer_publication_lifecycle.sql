ALTER TABLE answers
    ADD COLUMN IF NOT EXISTS publication_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS current_create_execution_intent_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS create_preparation_token VARCHAR(100),
    ADD COLUMN IF NOT EXISTS publication_failure_terminal_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS publication_failure_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS create_preparation_expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS pending_delete_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS current_delete_execution_intent_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS delete_preparation_token VARCHAR(100),
    ADD COLUMN IF NOT EXISTS delete_preparation_expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS delete_failure_terminal_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS delete_failure_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS reconciliation_required_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS reconciliation_required_intent_id VARCHAR(100);

CREATE TABLE IF NOT EXISTS qna_answer_update_states (
    id BIGSERIAL PRIMARY KEY,
    answer_id BIGINT NOT NULL REFERENCES answers(id) ON DELETE CASCADE,
    update_version BIGINT NOT NULL,
    update_token VARCHAR(100) NOT NULL,
    execution_intent_public_id VARCHAR(100),
    preparation_token VARCHAR(100),
    preparation_expires_at TIMESTAMP,
    status VARCHAR(40) NOT NULL,
    retryable BOOLEAN,
    error_code VARCHAR(120),
    error_reason VARCHAR(500),
    pending_content TEXT NOT NULL,
    expected_content_hash VARCHAR(66),
    reconciliation_required_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_qna_answer_update_states_answer_version UNIQUE (answer_id, update_version),
    CONSTRAINT uk_qna_answer_update_states_update_token UNIQUE (update_token),
    CONSTRAINT uk_qna_answer_update_states_execution_intent UNIQUE (execution_intent_public_id),
    CONSTRAINT ck_qna_answer_update_states_status
        CHECK (
            status IN (
                'PREPARING',
                'INTENT_BOUND',
                'PREPARATION_FAILED',
                'CONFIRMED',
                'FAILED',
                'STALE',
                'DISCARDED',
                'RECONCILIATION_REQUIRED'
            )
        )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_qna_answer_update_states_one_active
    ON qna_answer_update_states(answer_id)
    WHERE status IN ('PREPARING', 'INTENT_BOUND', 'PREPARATION_FAILED', 'FAILED', 'RECONCILIATION_REQUIRED');

CREATE INDEX IF NOT EXISTS idx_qna_answer_update_states_answer_status
    ON qna_answer_update_states(answer_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_qna_answer_update_states_preparation_expiry
    ON qna_answer_update_states(status, preparation_expires_at)
    WHERE preparation_expires_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS qna_answer_execution_intent_refs (
    id BIGSERIAL PRIMARY KEY,
    execution_intent_public_id VARCHAR(100) NOT NULL,
    post_id BIGINT NOT NULL,
    answer_id BIGINT NOT NULL,
    action_type VARCHAR(60) NOT NULL,
    status_snapshot VARCHAR(30),
    local_outcome VARCHAR(40),
    local_outcome_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_qna_answer_execution_intent_refs_intent UNIQUE (execution_intent_public_id),
    CONSTRAINT ck_qna_answer_execution_intent_refs_action
        CHECK (action_type IN ('QNA_ANSWER_SUBMIT', 'QNA_ANSWER_UPDATE', 'QNA_ANSWER_DELETE'))
);

CREATE INDEX IF NOT EXISTS idx_qna_answer_execution_intent_refs_post_action
    ON qna_answer_execution_intent_refs(post_id, action_type);

CREATE INDEX IF NOT EXISTS idx_qna_answer_execution_intent_refs_answer_action
    ON qna_answer_execution_intent_refs(answer_id, action_type);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM web3_execution_intents
        WHERE resource_type = 'ANSWER'
          AND action_type IN ('QNA_ANSWER_UPDATE', 'QNA_ANSWER_DELETE')
          AND status IN ('AWAITING_SIGNATURE', 'SIGNED', 'PENDING_ONCHAIN')
    ) THEN
        RAISE EXCEPTION
            'MOM-409 migration blocked: active legacy QNA_ANSWER_UPDATE/QNA_ANSWER_DELETE intent exists';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM web3_execution_intents e
        JOIN web3_qna_answers qa ON qa.answer_id::TEXT = e.resource_id
        WHERE e.resource_type = 'ANSWER'
          AND e.action_type = 'QNA_ANSWER_SUBMIT'
          AND e.status IN ('AWAITING_SIGNATURE', 'SIGNED', 'PENDING_ONCHAIN')
    ) THEN
        RAISE EXCEPTION
            'MOM-409 migration blocked: active QNA_ANSWER_SUBMIT already has answer projection';
    END IF;
END $$;

INSERT INTO qna_answer_execution_intent_refs (
    execution_intent_public_id,
    post_id,
    answer_id,
    action_type,
    status_snapshot
)
SELECT e.public_id, a.post_id, a.id, e.action_type, e.status
FROM web3_execution_intents e
JOIN answers a ON a.id::TEXT = e.resource_id
WHERE e.resource_type = 'ANSWER'
  AND e.action_type IN ('QNA_ANSWER_SUBMIT', 'QNA_ANSWER_UPDATE', 'QNA_ANSWER_DELETE')
ON CONFLICT (execution_intent_public_id) DO UPDATE
SET post_id = EXCLUDED.post_id,
    answer_id = EXCLUDED.answer_id,
    action_type = EXCLUDED.action_type,
    status_snapshot = EXCLUDED.status_snapshot,
    updated_at = NOW();

WITH latest_submit AS (
    SELECT DISTINCT ON (e.resource_id)
        e.resource_id::BIGINT AS answer_id,
        e.public_id,
        e.status,
        e.last_error_reason
    FROM web3_execution_intents e
    WHERE e.resource_type = 'ANSWER'
      AND e.action_type = 'QNA_ANSWER_SUBMIT'
      AND e.resource_id ~ '^[0-9]+$'
    ORDER BY e.resource_id, e.created_at DESC, e.id DESC
)
UPDATE answers a
SET publication_status = CASE
        WHEN qa.answer_id IS NOT NULL THEN 'VISIBLE'
        WHEN latest_submit.status IN ('AWAITING_SIGNATURE', 'SIGNED', 'PENDING_ONCHAIN') THEN 'PENDING'
        WHEN latest_submit.status IN ('FAILED_ONCHAIN', 'EXPIRED', 'CANCELED', 'NONCE_STALE') THEN 'FAILED'
        ELSE 'VISIBLE'
    END,
    current_create_execution_intent_id = CASE
        WHEN qa.answer_id IS NULL
         AND latest_submit.status IN ('AWAITING_SIGNATURE', 'SIGNED', 'PENDING_ONCHAIN')
        THEN latest_submit.public_id
        ELSE NULL
    END,
    publication_failure_terminal_status = CASE
        WHEN qa.answer_id IS NULL
         AND latest_submit.status IN ('FAILED_ONCHAIN', 'EXPIRED', 'CANCELED', 'NONCE_STALE')
        THEN latest_submit.status
        ELSE NULL
    END,
    publication_failure_reason = CASE
        WHEN qa.answer_id IS NULL
         AND latest_submit.status IN ('FAILED_ONCHAIN', 'EXPIRED', 'CANCELED', 'NONCE_STALE')
        THEN COALESCE(latest_submit.last_error_reason, 'answer publication migration backfill')
        ELSE NULL
    END
FROM latest_submit
LEFT JOIN web3_qna_answers qa ON qa.answer_id = latest_submit.answer_id
WHERE a.id = latest_submit.answer_id;

UPDATE answers
SET publication_status = 'VISIBLE'
WHERE publication_status IS NULL;

ALTER TABLE answers
    ALTER COLUMN publication_status SET DEFAULT 'VISIBLE',
    ALTER COLUMN publication_status SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_answers_publication_status'
    ) THEN
        ALTER TABLE answers
            ADD CONSTRAINT ck_answers_publication_status
            CHECK (publication_status IN ('PENDING', 'VISIBLE', 'FAILED', 'RECONCILIATION_REQUIRED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_answers_pending_delete_status'
    ) THEN
        ALTER TABLE answers
            ADD CONSTRAINT ck_answers_pending_delete_status
            CHECK (pending_delete_status IS NULL OR pending_delete_status IN ('PREPARING', 'PENDING', 'FAILED'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_answers_public_visible
    ON answers(post_id, is_accepted DESC, created_at ASC)
    WHERE publication_status = 'VISIBLE' AND pending_delete_status IS NULL;

CREATE INDEX IF NOT EXISTS idx_answers_owner_lifecycle
    ON answers(user_id, post_id, publication_status, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_answers_create_preparation_expiry
    ON answers(create_preparation_expires_at)
    WHERE create_preparation_token IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_answers_pending_create_by_post
    ON answers(post_id, publication_status, current_create_execution_intent_id)
    WHERE publication_status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_answers_delete_preparation_expiry
    ON answers(delete_preparation_expires_at)
    WHERE delete_preparation_token IS NOT NULL;

ALTER TABLE qna_answer_update_states
    ADD COLUMN IF NOT EXISTS pending_image_update BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS qna_answer_update_images (
    update_state_id BIGINT NOT NULL REFERENCES qna_answer_update_states(id) ON DELETE CASCADE,
    image_id BIGINT NOT NULL REFERENCES images(id) ON DELETE CASCADE,
    image_order INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (update_state_id, image_id),
    CONSTRAINT uq_qna_answer_update_images_order UNIQUE (update_state_id, image_order),
    CONSTRAINT chk_qna_answer_update_images_order CHECK (image_order > 0)
);

CREATE INDEX IF NOT EXISTS idx_qna_answer_update_images_image
    ON qna_answer_update_images(image_id);

CREATE INDEX IF NOT EXISTS idx_images_answer_update_reference
    ON images(reference_type, reference_id)
    WHERE reference_type = 'COMMUNITY_ANSWER_UPDATE';
