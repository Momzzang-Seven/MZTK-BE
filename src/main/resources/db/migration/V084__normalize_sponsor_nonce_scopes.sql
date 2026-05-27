-- MOM-458: normalize transaction nonce scopes and clean up invalid lookup indexes.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT chain_id, LOWER(from_address), nonce
            FROM web3_transactions
            WHERE nonce IS NOT NULL
            GROUP BY chain_id, LOWER(from_address), nonce
            HAVING COUNT(*) > 1
        ) duplicate_nonce_scope
    ) THEN
        RAISE EXCEPTION
            'Duplicate web3 transaction nonce scopes would be created by lower-case normalization';
    END IF;
END $$;

UPDATE web3_transactions
SET from_address = LOWER(from_address),
    to_address = LOWER(to_address),
    authority_address = LOWER(authority_address),
    delegate_target = LOWER(delegate_target)
WHERE from_address <> LOWER(from_address)
   OR to_address <> LOWER(to_address)
   OR (authority_address IS NOT NULL AND authority_address <> LOWER(authority_address))
   OR (delegate_target IS NOT NULL AND delegate_target <> LOWER(delegate_target));

WITH normalized_nonce_state AS (
    SELECT
        LOWER(from_address) AS from_address,
        MAX(next_nonce) AS next_nonce,
        MAX(updated_at) AS updated_at
    FROM web3_nonce_state
    GROUP BY LOWER(from_address)
),
deleted_nonce_state AS (
    DELETE FROM web3_nonce_state
)
INSERT INTO web3_nonce_state(from_address, next_nonce, updated_at)
SELECT from_address, next_nonce, updated_at
FROM normalized_nonce_state;
