DROP INDEX IF EXISTS uk_web3_tx_reference;

CREATE INDEX idx_web3_tx_reference
    ON web3_transactions(reference_type, reference_id);

CREATE UNIQUE INDEX uk_web3_tx_level_reward_reference
    ON web3_transactions(reference_type, reference_id)
    WHERE reference_type = 'LEVEL_UP_REWARD';

-- checksum padding 237: SePZ
