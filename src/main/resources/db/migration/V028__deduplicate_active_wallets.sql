-- Validation query (run manually before deploying to verify data state):
--
-- SELECT user_id, COUNT(*) AS active_count
-- FROM user_wallets
-- WHERE status = 'ACTIVE'
-- GROUP BY user_id
-- HAVING COUNT(*) > 1;
--
-- If the above returns rows, this migration corrects them.
-- Strategy: keep the most recently registered ACTIVE wallet per user;
-- transition older duplicates to UNLINKED so the subsequent unique index
-- (V029) can be created safely.
UPDATE user_wallets
SET
    status = 'UNLINKED',
    unlinked_at = NOW(),
    updated_at = NOW()
WHERE
    id IN (
        SELECT id
        FROM (
            SELECT
                id,
                ROW_NUMBER() OVER (
                    PARTITION BY user_id
                    ORDER BY registered_at DESC
                ) AS rn
            FROM user_wallets
            WHERE status = 'ACTIVE'
        ) AS ranked
        WHERE rn > 1
    );
