CREATE TABLE verification_requests (
    id                      BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    verification_id         VARCHAR(36)  NOT NULL UNIQUE,
    user_id                 BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    verification_kind       VARCHAR(30)  NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    exercise_date           DATE         NULL,
    shot_at_kst             TIMESTAMP    NULL,
    tmp_object_key          VARCHAR(512) NOT NULL UNIQUE,
    rejection_reason_code   VARCHAR(50)  NULL,
    rejection_reason_detail VARCHAR(500) NULL,
    failure_code            VARCHAR(50)  NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_verification_kind
        CHECK (verification_kind IN ('WORKOUT_PHOTO', 'WORKOUT_RECORD')),
    CONSTRAINT chk_verification_status
        CHECK (status IN ('PENDING', 'ANALYZING', 'VERIFIED', 'REJECTED', 'FAILED'))
);

CREATE INDEX idx_verification_requests_user_updated
    ON verification_requests (user_id, updated_at DESC);
