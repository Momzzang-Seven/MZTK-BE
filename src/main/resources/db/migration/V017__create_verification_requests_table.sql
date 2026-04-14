-- verification_requests 테이블: V017 원본 + V018 (reward_status / reward_source_ref 추가) 통합.
-- 신규 배포이므로 V018 의 기존 row 백필 UPDATE 는 제거 (생성 시점에는 데이터가 없음).

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
    reward_status           VARCHAR(20)  NOT NULL DEFAULT 'NOT_REQUESTED',
    reward_source_ref       VARCHAR(255) NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_verification_kind
        CHECK (verification_kind IN ('WORKOUT_PHOTO', 'WORKOUT_RECORD')),
    CONSTRAINT chk_verification_status
        CHECK (status IN ('PENDING', 'ANALYZING', 'VERIFIED', 'REJECTED', 'FAILED')),
    CONSTRAINT chk_verification_reward_status
        CHECK (reward_status IN ('NOT_REQUESTED', 'PENDING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_verification_requests_user_updated
    ON verification_requests (user_id, updated_at DESC);
