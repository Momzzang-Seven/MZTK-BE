-- posts 테이블 진화: title nullable, 수락 플로우 컬럼/상태, 최종 status CHECK.
-- 원래 V013 (title DROP NOT NULL) + V024 (수락 컬럼 추가) + V025 (FK drop) + V044 (PENDING_ACCEPT 확장) 를 하나로 통합.
-- accepted_answer_id 는 FK 로 걸지 않는다 (answers 제거/역참조 간소화를 위해 의도적으로 생략).

ALTER TABLE public.posts ALTER COLUMN title DROP NOT NULL;

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS accepted_answer_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

UPDATE posts
SET status = CASE
    WHEN is_solved = TRUE THEN 'RESOLVED'
    ELSE 'OPEN'
END
WHERE status IS NULL;

ALTER TABLE posts
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_status
        CHECK (status IN ('OPEN', 'PENDING_ACCEPT', 'RESOLVED'));
