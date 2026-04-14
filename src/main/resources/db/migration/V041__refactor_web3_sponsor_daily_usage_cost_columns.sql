ALTER TABLE web3_sponsor_daily_usage
    RENAME COLUMN estimated_cost_wei TO consumed_cost_wei;

ALTER TABLE web3_sponsor_daily_usage
    ADD COLUMN reserved_cost_wei NUMERIC(78, 0) NOT NULL DEFAULT 0;

ALTER TABLE web3_sponsor_daily_usage
    ADD CONSTRAINT ck_web3_sponsor_daily_usage_non_negative
        CHECK (reserved_cost_wei >= 0 AND consumed_cost_wei >= 0);
