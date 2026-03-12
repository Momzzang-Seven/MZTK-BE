CREATE TABLE images (
    id               BIGINT    PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reference_type   VARCHAR(30)  NOT NULL,
    reference_id     BIGINT       NULL,         -- polymorphic ref; no FK constraint
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    tmp_object_key   VARCHAR(512) NOT NULL UNIQUE,
    final_object_key VARCHAR(512) NULL UNIQUE,
    img_order        INTEGER      NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Lambda 콜백 시 tmp_object_key 기반 조회에 사용
CREATE INDEX idx_images_tmp_object_key ON images (tmp_object_key);

-- 미처리 PENDING 이미지 정리 스케줄러에서 사용
CREATE INDEX idx_images_status_created_at ON images (status, created_at);

-- 특정 엔티티(post 등)에 연결된 이미지 일괄 조회에 사용
CREATE INDEX idx_images_reference ON images (reference_type, reference_id);