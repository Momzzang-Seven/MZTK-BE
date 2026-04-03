CREATE TABLE users_account (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT NOT NULL REFERENCES users(id),
    provider             VARCHAR(20)  NOT NULL,
    provider_user_id     VARCHAR(255),
    password_hash        VARCHAR(255),
    google_refresh_token TEXT,
    last_login_at        TIMESTAMP,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at           TIMESTAMP,
    created_at           TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_account_user_id
        UNIQUE (user_id),
    CONSTRAINT uk_users_account_provider_provider_user_id
        UNIQUE (provider, provider_user_id),
    CONSTRAINT ck_users_account_status
        CHECK (status IN ('ACTIVE', 'DELETED', 'BLOCKED', 'UNVERIFIED'))
);

INSERT INTO users_account (
    user_id,
    provider,
    provider_user_id,
    password_hash,
    google_refresh_token,
    last_login_at,
    status,
    deleted_at,
    created_at,
    updated_at
)
SELECT
    id,
    COALESCE(provider::VARCHAR, 'LOCAL'),
    CASE WHEN provider = 'LOCAL' THEN NULL ELSE provider_user_id END,
    password_hash,
    google_refresh_token,
    last_login_at,
    COALESCE(status::VARCHAR, 'ACTIVE'),
    deleted_at,
    created_at,
    updated_at
FROM users;
