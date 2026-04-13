-- images 테이블: V015 원본 + V019 (error_reason) + V020 (reference_type nullable + partial index) 통합.
--
-- reference_type / reference_id 는 polymorphic reference 이므로 FK 제약은 걸지 않는다.
-- Lambda 콜백과의 race 를 피하기 위해 물리 삭제 대신 unlink 하려고 nullable 로 둔다.
-- error_reason 은 Lambda 이미지 처리 실패 시 원인 메시지 (FAILED 상태 전이 시에만 채워짐).

CREATE TABLE images (
    id               BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id          BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reference_type   VARCHAR(30)   NULL,
    reference_id     BIGINT        NULL,
    status           VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    tmp_object_key   VARCHAR(512)  NOT NULL UNIQUE,
    final_object_key VARCHAR(512)  NULL UNIQUE,
    img_order        INTEGER       NULL,
    error_reason     VARCHAR(1024) NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Lambda 콜백 시 tmp_object_key 기반 조회에 사용
CREATE INDEX idx_images_tmp_object_key ON images (tmp_object_key);

-- 미처리 PENDING 이미지 정리 스케줄러에서 사용
CREATE INDEX idx_images_status_created_at ON images (status, created_at);

-- 특정 엔티티(post 등)에 연결된 이미지 일괄 조회에 사용.
-- reference_id IS NULL 인 unlinked row 는 인덱스에서 제외 (findImagesByReference / unlinkBy*).
CREATE INDEX idx_images_reference
    ON images (reference_type, reference_id)
    WHERE reference_id IS NOT NULL;
