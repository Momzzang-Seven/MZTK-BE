-- V014: web3_treasury_keys 테이블에 wallet_alias 컬럼 추가
--
-- 배경:
--   V007 에서 web3_treasury_keys 를 생성할 때 wallet_alias 컬럼이 누락되었습니다.
--   TreasuryKeyPersistenceAdapter.loadByAlias() 는 wallet_alias 로 트레저리 키를 조회하므로,
--   이 컬럼 없이는 TransactionIssuerWorker 가 전혀 동작하지 않습니다.
--
-- 마이그레이션 전략:
--   1. NOT NULL 제약 없이 컬럼 추가          (기존 row 가 있어도 실패 없음)
--   2. 기존 row(id=1) 에 기본 alias 설정     (provisioning API 이전에 삽입된 row 대응)
--   3. NOT NULL 제약 + UNIQUE 인덱스 추가

-- 1. 컬럼 추가 (nullable) -> 기존 row의 wallet_alias는 NULL로 설정됩니다.
ALTER TABLE web3_treasury_keys
    ADD COLUMN IF NOT EXISTS wallet_alias VARCHAR(64);

-- 2. 기존 row 에 기본 alias 할당
--    application.yml 의 web3.reward-token.treasury.wallet-alias 값과 일치해야 합니다.
UPDATE web3_treasury_keys
SET wallet_alias = 'reward-treasury'
WHERE id = 1
  AND wallet_alias IS NULL;

-- 3. NOT NULL 제약 추가
ALTER TABLE web3_treasury_keys
    ALTER COLUMN wallet_alias SET NOT NULL;

-- 4. UNIQUE 인덱스 추가
CREATE UNIQUE INDEX IF NOT EXISTS uk_web3_treasury_keys_wallet_alias
    ON web3_treasury_keys (wallet_alias);
