-- Lambda 이미지 처리 실패 시 원인 메시지를 저장하는 컬럼 추가.
-- FAILED 상태 전이 시에만 채워지며, COMPLETED / PENDING 행은 NULL 유지.
ALTER TABLE images
    ADD COLUMN error_reason VARCHAR(1024) NULL;
