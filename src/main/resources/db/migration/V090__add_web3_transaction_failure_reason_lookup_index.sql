-- flyway:executeInTransaction=false

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_failure_reason_status
    ON web3_transactions(failure_reason, status);
