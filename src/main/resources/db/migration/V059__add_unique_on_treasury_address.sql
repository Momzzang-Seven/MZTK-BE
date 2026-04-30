--   treasury_address unique 제약 이름 정리
--   V007 에서 treasury_address 를 inline UNIQUE 로 선언했으므로 PostgreSQL 이
--   web3_treasury_keys_treasury_address_key 라는 자동 생성 이름을 붙였다.
--   V055 에서 테이블명을 web3_treasury_wallets 로 변경했지만 제약 이름은 그대로 남는다.
--   여기서 DROP → 명명된 uk_web3_treasury_wallets_treasury_address 로 재생성한다.

ALTER TABLE web3_treasury_wallets
DROP CONSTRAINT IF EXISTS web3_treasury_keys_treasury_address_key;

ALTER TABLE web3_treasury_wallets
    ADD CONSTRAINT uk_web3_treasury_wallets_treasury_address
        UNIQUE (treasury_address);
