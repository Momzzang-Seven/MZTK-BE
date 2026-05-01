-- MOM-383 follow-up: post-commit KMS side-effect audit table.
-- Records KMS DisableKey / ScheduleKeyDeletion / CreateAlias / UpdateAlias
-- attempts that run AFTER_COMMIT, separately from web3_treasury_provision_audits
-- (which captures the business-flow attempt itself).

CREATE TABLE IF NOT EXISTS web3_treasury_kms_audits (
    id              BIGSERIAL PRIMARY KEY,
    operator_id     BIGINT REFERENCES users(id) ON DELETE SET NULL,
    wallet_alias    VARCHAR(64) NOT NULL,
    kms_key_id      VARCHAR(128),
    wallet_address  VARCHAR(42),
    action_type     VARCHAR(32) NOT NULL,
    success         BOOLEAN NOT NULL,
    failure_reason  VARCHAR(256),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_web3_treasury_kms_audits_action
        CHECK (action_type IN ('KMS_DISABLE', 'KMS_SCHEDULE_DELETION',
                               'KMS_CREATE_ALIAS', 'KMS_UPDATE_ALIAS')),
    CONSTRAINT ck_web3_treasury_kms_audits_address_format
        CHECK (wallet_address IS NULL OR wallet_address ~ '^0x[0-9a-fA-F]{40}$')
);

CREATE INDEX IF NOT EXISTS idx_web3_treasury_kms_audits_failures
    ON web3_treasury_kms_audits (created_at DESC)
    WHERE success = FALSE;
