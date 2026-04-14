-- web3_treasury_keys 진화:
--   V014 (wallet_alias 컬럼 + UNIQUE) + V045 (id SMALLINT → BIGINT) 를 통합.
--
-- V007 에서 web3_treasury_keys 를 생성할 때 wallet_alias 컬럼이 누락되었고,
-- id 타입도 SMALLINT 였기 때문에 두 가지를 함께 정정한다.
-- TreasuryKeyPersistenceAdapter.loadByAlias() 는 wallet_alias 로 조회하므로,
-- 이 컬럼 없이는 TransactionIssuerWorker 가 전혀 동작하지 않는다.

-- 1. id 타입을 BIGINT 로 확장
ALTER TABLE web3_treasury_keys
    ALTER COLUMN id SET DATA TYPE BIGINT;

-- 2. wallet_alias 컬럼 추가 (nullable 로 먼저)
ALTER TABLE web3_treasury_keys
    ADD COLUMN IF NOT EXISTS wallet_alias VARCHAR(64);

-- 3. 기존 row(id=1) 에 기본 alias 할당
--    application.yml 의 web3.reward-token.treasury.wallet-alias 값과 일치해야 함.
UPDATE web3_treasury_keys
SET wallet_alias = 'reward-treasury'
WHERE id = 1
  AND wallet_alias IS NULL;

-- 4. NOT NULL + UNIQUE
ALTER TABLE web3_treasury_keys
    ALTER COLUMN wallet_alias SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_web3_treasury_keys_wallet_alias
    ON web3_treasury_keys (wallet_alias);
