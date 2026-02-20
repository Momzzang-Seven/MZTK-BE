DROP INDEX IF EXISTS uk_web3_tx_reference;

CREATE UNIQUE INDEX IF NOT EXISTS uk_web3_tx_reference_level_up
    ON web3_transactions(reference_type, reference_id)
    WHERE reference_type = 'LEVEL_UP_REWARD';
