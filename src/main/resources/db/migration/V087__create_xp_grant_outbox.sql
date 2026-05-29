-- Guaranteed-delivery outbox for XP grants that failed synchronously.
-- A reconciliation scheduler retries PENDING rows; the grant itself is idempotent
-- (xp_ledger unique on (user_id, idempotency_key)), so retries never double-grant.
CREATE TABLE IF NOT EXISTS xp_grant_outbox (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    xp_type VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    source_ref VARCHAR(255),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_xp_grant_outbox_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_xp_grant_outbox_status CHECK (status IN ('PENDING', 'DONE', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_xp_grant_outbox_due
    ON xp_grant_outbox (status, next_attempt_at);
