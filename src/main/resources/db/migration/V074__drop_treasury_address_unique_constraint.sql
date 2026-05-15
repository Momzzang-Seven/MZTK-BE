-- MOM-444: 동일 treasury_address 를 여러 wallet_alias 가 공유할 수 있도록 cross-row UNIQUE 제거.
-- wallet_alias / kms_key_id 의 UNIQUE 는 유지된다 (둘 다 V069 에서 명명된 제약으로 명시).
-- per-row 포맷 CHECK (ck_web3_treasury_wallets_kms_key_id_required) 도 영향 없음.
--
-- Forward-only by policy. 롤백은 별도 V0XX 가 `ADD CONSTRAINT ... UNIQUE (treasury_address)` 를
-- 다시 거는 형태로 진행하되, 그 시점에는 공유-주소 row 가 없음을 사전 검증해야 한다.

ALTER TABLE web3_treasury_wallets
    DROP CONSTRAINT IF EXISTS uk_web3_treasury_wallets_treasury_address;
