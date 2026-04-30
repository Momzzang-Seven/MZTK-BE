-- MOM-340 / MOM-383: V049 의 ck_web3_treasury_keys_slot_pair 제약 DROP
--   + treasury_address unique 제약을 legacy 자동 생성 이름에서 명명된 이름으로 교체.
--
-- [1] slot_pair CHECK DROP
--   V049 는 (treasury_address IS NULL ↔ treasury_private_key_encrypted IS NULL) 페어링을
--   강제하는 CHECK 를 추가했다. 그러나 본 PR (PR1) 의 KMS provision 경로는
--     treasury_address      = derivedAddress (NOT NULL)
--     treasury_private_key_encrypted = NULL  (KMS 경로는 절대 채우지 않음)
--   형태로 INSERT 하므로 CHECK 의 어느 분기도 만족하지 못해 Postgres 가 INSERT 를 거부한다.
--
--   PR1 머지 ~ PR4 머지 사이는 *legacy row + KMS row* 두 형태가 한 테이블에 공존하므로,
--   V049 의 CHECK 만 일단 DROP 한다. PR4 cleanup 에서 kms_key_id IS NOT NULL CHECK 를
--   새로 정의하면서 row shape 정합성을 다시 강제한다.
--

ALTER TABLE web3_treasury_wallets
    DROP CONSTRAINT IF EXISTS ck_web3_treasury_keys_slot_pair;
