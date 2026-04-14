-- web3 범위의 admin action audit 테이블을 통합 admin_action_audits 로 교체.
-- V035 원본 (테이블 교체) + V037 (operator_id nullable) 을 단일 파일로 통합.
--
-- Pre-production: web3_admin_action_audits 에는 보존해야 할 데이터가 없으므로
-- DROP + CREATE 를 한 번에 수행한다.
-- operator_id 는 recovery-path audit 기록 (JWT principal 이 없는 경우) 을 위해
-- 처음부터 NULL 허용으로 만든다.

DROP TABLE IF EXISTS web3_admin_action_audits;

CREATE TABLE IF NOT EXISTS admin_action_audits (
    id          BIGSERIAL    PRIMARY KEY,
    operator_id BIGINT       NULL,
    action_type VARCHAR(60)  NOT NULL,
    target_type VARCHAR(40)  NOT NULL,
    target_id   VARCHAR(100),
    success     BOOLEAN      NOT NULL,
    detail_json TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_admin_action_audits_operator_id
    ON admin_action_audits(operator_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_action_audits_created_at
    ON admin_action_audits(created_at DESC);
