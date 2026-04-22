ALTER TABLE web3_treasury_keys
    ALTER COLUMN treasury_address DROP NOT NULL;

ALTER TABLE web3_treasury_keys
    ALTER COLUMN treasury_private_key_encrypted DROP NOT NULL;

ALTER TABLE web3_treasury_keys
    DROP CONSTRAINT IF EXISTS ck_web3_treasury_keys_singleton;

ALTER TABLE web3_treasury_keys
    ADD CONSTRAINT ck_web3_treasury_keys_slot_pair
        CHECK (
            (treasury_address IS NULL AND treasury_private_key_encrypted IS NULL)
            OR (treasury_address IS NOT NULL AND treasury_private_key_encrypted IS NOT NULL)
        );
