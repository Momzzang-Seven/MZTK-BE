-- MOM-458: sponsor nonce slot/window persistence.

ALTER TABLE web3_transactions
    ADD COLUMN IF NOT EXISTS chain_id BIGINT NOT NULL DEFAULT ${web3ChainId};

DROP INDEX IF EXISTS uk_web3_tx_sender_nonce;

DROP INDEX IF EXISTS uk_web3_tx_eip7702_authority_nonce;

ALTER TABLE web3_transaction_audits
    DROP CONSTRAINT IF EXISTS web3_transaction_audits_web3_transaction_id_fkey;

ALTER TABLE web3_transaction_audits
    ADD CONSTRAINT fk_web3_transaction_audits_tx
        FOREIGN KEY (web3_transaction_id) REFERENCES web3_transactions(id) ON DELETE RESTRICT;

CREATE TABLE IF NOT EXISTS web3_sponsor_nonce_locks (
    chain_id BIGINT NOT NULL,
    from_address VARCHAR(42) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (chain_id, from_address),
    CONSTRAINT ck_web3_sponsor_nonce_locks_from_lower
        CHECK (from_address = LOWER(from_address))
);

CREATE TABLE IF NOT EXISTS web3_nonce_slot_attempts (
    id BIGSERIAL PRIMARY KEY,
    chain_id BIGINT NOT NULL,
    from_address VARCHAR(42) NOT NULL,
    nonce BIGINT NOT NULL CHECK (nonce >= 0),
    attempt_no INTEGER NOT NULL CHECK (attempt_no > 0),
    tx_id BIGINT NOT NULL,
    tx_hash VARCHAR(66),
    status VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(260) NOT NULL,
    receipt_observed_at TIMESTAMP,
    receipt_status VARCHAR(30),
    terminal_reason VARCHAR(120),
    superseded_by_attempt_id BIGINT,
    signed_at TIMESTAMP,
    broadcast_started_at TIMESTAMP,
    broadcasted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_web3_nonce_slot_attempts_from_lower
        CHECK (from_address = LOWER(from_address)),
    CONSTRAINT ck_web3_nonce_slot_attempts_status CHECK (
        status IN (
            'RESERVED', 'REPLACEMENT_PREPARING', 'SIGNED', 'BROADCASTING', 'BROADCASTED',
            'CONSUMED', 'CONSUMED_UNKNOWN', 'STUCK', 'OPERATOR_REVIEW_REQUIRED',
            'DROPPED', 'SUPERSEDED', 'ABANDONED'
        )
    ),
    CONSTRAINT ck_web3_nonce_slot_attempt_terminal_reason CHECK (
        (
            status IN ('RESERVED', 'REPLACEMENT_PREPARING', 'SIGNED', 'BROADCASTING',
                       'BROADCASTED', 'CONSUMED')
            AND terminal_reason IS NULL
        )
        OR (
            status IN ('CONSUMED_UNKNOWN', 'STUCK', 'OPERATOR_REVIEW_REQUIRED',
                       'DROPPED', 'SUPERSEDED', 'ABANDONED')
            AND terminal_reason IS NOT NULL
        )
    ),
    CONSTRAINT ck_web3_nonce_slot_attempt_consumed_receipt CHECK (
        status <> 'CONSUMED'
        OR (receipt_observed_at IS NOT NULL AND receipt_status IS NOT NULL)
    ),
    CONSTRAINT ck_web3_nonce_slot_attempt_unbroadcastable_status CHECK (
        status NOT IN ('RESERVED', 'REPLACEMENT_PREPARING', 'DROPPED')
        OR (
            tx_hash IS NULL
            AND signed_at IS NULL
            AND broadcast_started_at IS NULL
            AND broadcasted_at IS NULL
        )
    ),
    CONSTRAINT ck_web3_nonce_slot_attempt_superseded CHECK (
        (status <> 'SUPERSEDED' AND superseded_by_attempt_id IS NULL)
        OR (status = 'SUPERSEDED' AND superseded_by_attempt_id IS NOT NULL)
    ),
    CONSTRAINT ck_web3_nonce_slot_attempt_not_self_superseded
        CHECK (superseded_by_attempt_id IS NULL OR superseded_by_attempt_id <> id),
    CONSTRAINT uk_web3_nonce_slot_attempt_no UNIQUE (chain_id, from_address, nonce, attempt_no),
    CONSTRAINT uk_web3_nonce_slot_attempt_id_scope UNIQUE (id, chain_id, from_address, nonce),
    CONSTRAINT uk_web3_nonce_slot_attempt_id_tx UNIQUE (id, tx_id),
    CONSTRAINT uk_web3_nonce_slot_attempt_idem UNIQUE (idempotency_key),
    CONSTRAINT uk_web3_nonce_slot_attempt_tx UNIQUE (tx_id),
    CONSTRAINT fk_web3_nonce_slot_attempt_superseded_by_scope
        FOREIGN KEY (superseded_by_attempt_id, chain_id, from_address, nonce)
        REFERENCES web3_nonce_slot_attempts(id, chain_id, from_address, nonce)
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_web3_nonce_slot_attempt_scope_created
    ON web3_nonce_slot_attempts(chain_id, from_address, nonce, created_at);

CREATE TABLE IF NOT EXISTS web3_nonce_slot_evidence (
    id BIGSERIAL PRIMARY KEY,
    chain_id BIGINT NOT NULL,
    from_address VARCHAR(42) NOT NULL,
    nonce BIGINT NOT NULL CHECK (nonce >= 0),
    evidence_type VARCHAR(60) NOT NULL,
    evidence_source VARCHAR(20) NOT NULL,
    provider_alias VARCHAR(40),
    payload_json TEXT NOT NULL,
    related_evidence_id BIGINT,
    created_by VARCHAR(120),
    observed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_web3_nonce_slot_evidence_from_lower
        CHECK (from_address = LOWER(from_address)),
    CONSTRAINT ck_web3_nonce_slot_evidence_type CHECK (
        evidence_type IN (
            'RPC_SNAPSHOT',
            'UNKNOWN_CONSUMED_CLOSURE',
            'ADMIN_UNKNOWN_CONSUMED_CLOSURE',
            'ADMIN_CONSUMED_PROOF',
            'UNKNOWN_TO_CONSUMED_CORRECTION',
            'DATA_CORRECTION'
        )
    ),
    CONSTRAINT ck_web3_nonce_slot_evidence_source
        CHECK (evidence_source IN ('SYSTEM', 'ADMIN')),
    CONSTRAINT ck_web3_nonce_slot_evidence_admin_created_by
        CHECK (evidence_source <> 'ADMIN' OR (created_by IS NOT NULL AND created_by <> '')),
    CONSTRAINT ck_web3_nonce_slot_evidence_type_source CHECK (
        (evidence_type IN ('RPC_SNAPSHOT', 'UNKNOWN_CONSUMED_CLOSURE')
            AND evidence_source = 'SYSTEM')
        OR (evidence_type IN ('ADMIN_UNKNOWN_CONSUMED_CLOSURE', 'ADMIN_CONSUMED_PROOF',
                              'DATA_CORRECTION')
            AND evidence_source = 'ADMIN')
        OR evidence_type = 'UNKNOWN_TO_CONSUMED_CORRECTION'
    ),
    CONSTRAINT ck_web3_nonce_slot_evidence_payload_nonblank
        CHECK (payload_json <> ''),
    CONSTRAINT ck_web3_nonce_slot_evidence_related_type CHECK (
        related_evidence_id IS NULL
        OR evidence_type IN ('UNKNOWN_TO_CONSUMED_CORRECTION', 'DATA_CORRECTION')
    ),
    CONSTRAINT ck_web3_nonce_slot_evidence_unknown_correction_link CHECK (
        evidence_type <> 'UNKNOWN_TO_CONSUMED_CORRECTION'
        OR related_evidence_id IS NOT NULL
    ),
    CONSTRAINT ck_web3_nonce_slot_evidence_related_not_self
        CHECK (related_evidence_id IS NULL OR related_evidence_id <> id),
    CONSTRAINT uk_web3_nonce_slot_evidence_scope_id UNIQUE (chain_id, from_address, nonce, id),
    CONSTRAINT fk_web3_nonce_slot_evidence_related_scope
        FOREIGN KEY (chain_id, from_address, nonce, related_evidence_id)
        REFERENCES web3_nonce_slot_evidence(chain_id, from_address, nonce, id)
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_web3_nonce_slot_evidence_scope_type_observed
    ON web3_nonce_slot_evidence(chain_id, from_address, nonce, evidence_type, observed_at);

CREATE OR REPLACE FUNCTION reject_web3_nonce_slot_evidence_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'web3_nonce_slot_evidence is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_web3_nonce_slot_evidence_append_only
    ON web3_nonce_slot_evidence;

CREATE TRIGGER trg_web3_nonce_slot_evidence_append_only
    BEFORE UPDATE OR DELETE ON web3_nonce_slot_evidence
    FOR EACH ROW
    EXECUTE FUNCTION reject_web3_nonce_slot_evidence_mutation();

CREATE TABLE IF NOT EXISTS web3_nonce_slots (
    chain_id BIGINT NOT NULL,
    from_address VARCHAR(42) NOT NULL,
    nonce BIGINT NOT NULL CHECK (nonce >= 0),
    status VARCHAR(40) NOT NULL,
    attempt_no INTEGER NOT NULL CHECK (attempt_no >= 0),
    active_attempt_id BIGINT,
    active_tx_id BIGINT,
    active_tx_hash VARCHAR(66),
    consumed_attempt_id BIGINT,
    consumed_tx_id BIGINT,
    consumed_external_evidence_id BIGINT,
    consumed_at TIMESTAMP,
    consumed_reason VARCHAR(120),
    released_attempt_id BIGINT,
    released_tx_id BIGINT,
    released_at TIMESTAMP,
    release_reason VARCHAR(120),
    stuck_reason VARCHAR(120),
    replacement_claim_owner VARCHAR(120),
    replacement_claim_expires_at TIMESTAMP,
    replacement_prepare_attempt_count INTEGER NOT NULL DEFAULT 0,
    broadcast_started_at TIMESTAMP,
    last_broadcasted_at TIMESTAMP,
    broadcast_recovery_claim_owner VARCHAR(120),
    broadcast_recovery_claim_token VARCHAR(120),
    broadcast_recovery_claim_expires_at TIMESTAMP,
    broadcast_recovery_attempt_count INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (chain_id, from_address, nonce),
    CONSTRAINT ck_web3_nonce_slots_from_lower
        CHECK (from_address = LOWER(from_address)),
    CONSTRAINT ck_web3_nonce_slots_status CHECK (
        status IN (
            'RESERVED', 'REPLACEMENT_PREPARING', 'SIGNED', 'BROADCASTING', 'BROADCASTED',
            'CONSUMED', 'CONSUMED_UNKNOWN', 'STUCK', 'OPERATOR_REVIEW_REQUIRED', 'DROPPED'
        )
    ),
    CONSTRAINT ck_web3_nonce_slots_consumed_unknown_metadata CHECK (
        status <> 'CONSUMED_UNKNOWN'
        OR (
            consumed_external_evidence_id IS NOT NULL
            AND consumed_attempt_id IS NULL
            AND consumed_tx_id IS NULL
            AND consumed_at IS NOT NULL
        )
    ),
    CONSTRAINT ck_web3_nonce_slots_consumed_metadata CHECK (
        status <> 'CONSUMED'
        OR (
            consumed_at IS NOT NULL
            AND (
                (consumed_attempt_id IS NOT NULL AND consumed_tx_id IS NOT NULL
                 AND consumed_external_evidence_id IS NULL)
                OR (consumed_attempt_id IS NULL AND consumed_tx_id IS NULL
                    AND consumed_external_evidence_id IS NOT NULL)
            )
        )
    ),
    CONSTRAINT ck_web3_nonce_slots_non_consumed_metadata CHECK (
        status IN ('CONSUMED', 'CONSUMED_UNKNOWN')
        OR (
            consumed_attempt_id IS NULL
            AND consumed_tx_id IS NULL
            AND consumed_external_evidence_id IS NULL
            AND consumed_at IS NULL
            AND consumed_reason IS NULL
        )
    ),
    CONSTRAINT ck_web3_nonce_slots_dropped_metadata CHECK (
        status <> 'DROPPED'
        OR (
            released_attempt_id IS NOT NULL
            AND released_tx_id IS NOT NULL
            AND released_at IS NOT NULL
            AND release_reason IS NOT NULL
            AND active_attempt_id IS NULL
            AND active_tx_id IS NULL
            AND active_tx_hash IS NULL
            AND broadcast_started_at IS NULL
            AND last_broadcasted_at IS NULL
        )
    ),
    CONSTRAINT ck_web3_nonce_slots_non_dropped_release_metadata CHECK (
        status = 'DROPPED'
        OR (
            released_attempt_id IS NULL
            AND released_tx_id IS NULL
            AND released_at IS NULL
            AND release_reason IS NULL
        )
    ),
    CONSTRAINT ck_web3_nonce_slots_stuck_reason CHECK (
        status IN ('STUCK', 'REPLACEMENT_PREPARING')
        OR stuck_reason IS NULL
    ),
    CONSTRAINT ck_web3_nonce_slots_replacement_claim CHECK (
        (
            status = 'REPLACEMENT_PREPARING'
            AND replacement_claim_owner IS NOT NULL
            AND replacement_claim_expires_at IS NOT NULL
            AND replacement_prepare_attempt_count > 0
        )
        OR (
            status <> 'REPLACEMENT_PREPARING'
            AND replacement_claim_owner IS NULL
            AND replacement_claim_expires_at IS NULL
            AND replacement_prepare_attempt_count = 0
        )
    ),
    CONSTRAINT ck_web3_nonce_slots_broadcast_recovery_claim CHECK (
        (
            status = 'BROADCASTING'
            AND broadcast_recovery_claim_owner IS NOT NULL
            AND broadcast_recovery_claim_token IS NOT NULL
            AND broadcast_recovery_claim_expires_at IS NOT NULL
            AND broadcast_recovery_attempt_count > 0
        )
        OR (
            status <> 'BROADCASTING'
            AND broadcast_recovery_claim_owner IS NULL
            AND broadcast_recovery_claim_token IS NULL
            AND broadcast_recovery_claim_expires_at IS NULL
            AND broadcast_recovery_attempt_count = 0
        )
    ),
    CONSTRAINT fk_web3_nonce_slots_active_attempt
        FOREIGN KEY (active_attempt_id, chain_id, from_address, nonce)
        REFERENCES web3_nonce_slot_attempts(id, chain_id, from_address, nonce)
        ON DELETE RESTRICT,
    CONSTRAINT fk_web3_nonce_slots_consumed_attempt
        FOREIGN KEY (consumed_attempt_id, chain_id, from_address, nonce)
        REFERENCES web3_nonce_slot_attempts(id, chain_id, from_address, nonce)
        ON DELETE RESTRICT,
    CONSTRAINT fk_web3_nonce_slots_consumed_external_evidence
        FOREIGN KEY (chain_id, from_address, nonce, consumed_external_evidence_id)
        REFERENCES web3_nonce_slot_evidence(chain_id, from_address, nonce, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_web3_nonce_slots_released_attempt
        FOREIGN KEY (released_attempt_id, chain_id, from_address, nonce)
        REFERENCES web3_nonce_slot_attempts(id, chain_id, from_address, nonce)
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_web3_nonce_slots_scope_status_nonce
    ON web3_nonce_slots(chain_id, from_address, status, nonce);

CREATE INDEX IF NOT EXISTS idx_web3_nonce_slots_stale_broadcast
    ON web3_nonce_slots(chain_id, from_address, status, broadcast_started_at, broadcast_recovery_claim_expires_at);

CREATE INDEX IF NOT EXISTS idx_web3_nonce_slots_active_tx
    ON web3_nonce_slots(active_tx_id);

CREATE INDEX IF NOT EXISTS idx_web3_nonce_slots_active_hash
    ON web3_nonce_slots(active_tx_hash);
