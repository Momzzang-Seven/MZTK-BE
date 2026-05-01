-- MOM-340 / MOM-383: 토큰(키) 중심 네이밍을 wallet 네이밍으로 통일.
-- TreasuryWallet aggregate 가 KMS-backed wallet 을 의미하므로
-- 물리 테이블명도 web3_treasury_wallets 로 정렬한다.
--
-- ALTER TABLE ... RENAME TO 는 PostgreSQL 에서 기존 제약/인덱스 이름은 유지한다
-- (ck_web3_treasury_keys_*, uk_web3_treasury_keys_wallet_alias 등). 이는 의도적이며,
-- 향후 V057+ 에서 정리한다 (운영 DB 다운타임 최소화 목적).

ALTER TABLE web3_treasury_keys RENAME TO web3_treasury_wallets;
