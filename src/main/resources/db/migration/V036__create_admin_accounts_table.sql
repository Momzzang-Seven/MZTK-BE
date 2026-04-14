-- Admin accounts table for managing admin credentials separately from users_account.
CREATE TABLE admin_accounts (
    id                        BIGSERIAL    PRIMARY KEY,
    user_id                   BIGINT       NOT NULL UNIQUE REFERENCES users(id),
    login_id                  VARCHAR(32)  NOT NULL,
    password_hash             VARCHAR(255) NOT NULL,
    created_by                BIGINT       REFERENCES users(id),
    last_login_at             TIMESTAMPTZ,
    password_last_rotated_at  TIMESTAMPTZ,
    deleted_at                TIMESTAMPTZ,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Conditional unique index: login_id must be unique among active (non-deleted) accounts.
CREATE UNIQUE INDEX uk_admin_accounts_active_login_id
    ON admin_accounts (login_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_admin_accounts_created_by ON admin_accounts (created_by);
CREATE INDEX idx_admin_accounts_deleted_at ON admin_accounts (deleted_at);
