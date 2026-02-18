ALTER TABLE level_up_histories
    DROP COLUMN IF EXISTS reward_status;

ALTER TABLE level_up_histories
    DROP COLUMN IF EXISTS reward_tx_hash;
