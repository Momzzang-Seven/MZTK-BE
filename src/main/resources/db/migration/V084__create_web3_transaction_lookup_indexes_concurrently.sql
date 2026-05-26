-- flyway:executeInTransaction=false

-- MOM-458: create lookup indexes outside the V081 transactional migration.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_sender_nonce
    ON web3_transactions(chain_id, from_address, nonce)
    WHERE nonce IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_eip7702_authority_nonce
    ON web3_transactions(authority_address, authorization_nonce)
    WHERE tx_type = 'EIP7702'
      AND authority_address IS NOT NULL
      AND authorization_nonce IS NOT NULL;
