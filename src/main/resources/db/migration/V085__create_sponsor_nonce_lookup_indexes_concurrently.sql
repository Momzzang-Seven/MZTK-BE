-- flyway:executeInTransaction=false

-- MOM-458: create sponsor nonce lookup indexes outside a transaction.

DROP INDEX CONCURRENTLY IF EXISTS idx_web3_tx_sender_nonce;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_sender_nonce
    ON web3_transactions(chain_id, from_address, nonce)
    WHERE nonce IS NOT NULL;

DROP INDEX CONCURRENTLY IF EXISTS uk_web3_tx_eip7702_authority_nonce;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_web3_tx_eip7702_authority_nonce
    ON web3_transactions(chain_id, authority_address, authorization_nonce)
    WHERE tx_type = 'EIP7702'
      AND authority_address IS NOT NULL
      AND authorization_nonce IS NOT NULL;

DROP INDEX CONCURRENTLY IF EXISTS uk_web3_tx_non_reward_eip1559_sender_nonce;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_web3_tx_non_reward_eip1559_sender_nonce
    ON web3_transactions(chain_id, from_address, nonce)
    WHERE nonce IS NOT NULL
      AND tx_type = 'EIP1559'
      AND reference_type IS DISTINCT FROM 'LEVEL_UP_REWARD';

DROP INDEX CONCURRENTLY IF EXISTS uk_web3_tx_id_chain_sender_nonce;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_web3_tx_id_chain_sender_nonce
    ON web3_transactions(id, chain_id, from_address, nonce);
