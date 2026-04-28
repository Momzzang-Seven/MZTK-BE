-- MOM-340 / MOM-383: KMS 기반 서명으로 전환하기 위한 컬럼 추가.
--
-- 추가 원칙:
--   * 모두 nullable 로 추가하여 기존 row(legacy 평문/암호화 private key) 가 살아있도록 한다.
--   * 기존 row 에는 status='ACTIVE', key_origin='IMPORTED' 백필.
--   * legacy 컬럼(treasury_private_key_encrypted) 은 V049 에서 이미 NOT NULL 이 풀렸으므로
--     별도 변경 없음. 실제 컬럼 제거는 PR4 cleanup 마이그레이션에서 수행한다.

ALTER TABLE web3_treasury_wallets
    ADD COLUMN IF NOT EXISTS kms_key_id    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS status        VARCHAR(32),
    ADD COLUMN IF NOT EXISTS key_origin    VARCHAR(32),
    ADD COLUMN IF NOT EXISTS disabled_at   TIMESTAMP;

UPDATE web3_treasury_wallets
SET status     = 'ACTIVE',
    key_origin = 'IMPORTED'
WHERE status IS NULL
   OR key_origin IS NULL;
