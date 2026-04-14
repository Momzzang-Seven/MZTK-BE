CREATE TABLE IF NOT EXISTS web3_transfer_guard_audits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    client_ip VARCHAR(64) NOT NULL,
    domain_type VARCHAR(40) NOT NULL,
    reference_id VARCHAR(100) NOT NULL,
    prepare_id VARCHAR(36),
    reason VARCHAR(40) NOT NULL,
    requested_to_user_id BIGINT,
    resolved_to_user_id BIGINT,
    requested_amount_wei NUMERIC(78, 0) NOT NULL,
    resolved_amount_wei NUMERIC(78, 0),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_web3_transfer_guard_audits_created_at
    ON web3_transfer_guard_audits(created_at);

CREATE INDEX IF NOT EXISTS idx_web3_transfer_guard_audits_user_id
    ON web3_transfer_guard_audits(user_id);

CREATE INDEX IF NOT EXISTS idx_web3_transfer_guard_audits_domain_reference
    ON web3_transfer_guard_audits(domain_type, reference_id);
