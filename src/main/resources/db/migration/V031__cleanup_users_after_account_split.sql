-- V030 에서 users_account 테이블로 인증 정보를 옮긴 직후의 users 테이블 후속 정리.
-- V031 (auth 컬럼 drop) + V040 (role CHECK 확장) 통합.
--
-- 주의: 이 파일은 반드시 V030 이후에 실행되어야 한다 — V030 의 INSERT 가
--       아래에서 drop 되는 컬럼들을 참조하기 때문.

-- 1. Phase 5: users 테이블의 인증/라이프사이클 컬럼 제거
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_provider_provider_user_id_key;

ALTER TABLE users DROP COLUMN IF EXISTS provider;
ALTER TABLE users DROP COLUMN IF EXISTS provider_user_id;
ALTER TABLE users DROP COLUMN IF EXISTS password_hash;
ALTER TABLE users DROP COLUMN IF EXISTS google_refresh_token;
ALTER TABLE users DROP COLUMN IF EXISTS status;
ALTER TABLE users DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE users DROP COLUMN IF EXISTS last_login_at;

-- 2. role CHECK 제약을 ADMIN_SEED / ADMIN_GENERATED 포함하도록 확장
ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('USER', 'TRAINER', 'ADMIN', 'ADMIN_SEED', 'ADMIN_GENERATED'));
