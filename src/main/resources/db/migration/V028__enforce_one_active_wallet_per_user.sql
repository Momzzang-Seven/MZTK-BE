-- user_wallets: 유저당 ACTIVE 지갑 1개 제약을 걸기 위한 데이터 정리 + 부분 UNIQUE 인덱스.
-- V028 (중복 ACTIVE 정리) + V029 (partial unique index) 통합.
--
-- Strategy: 유저당 가장 최근에 등록된 ACTIVE 만 유지하고,
-- 나머지는 UNLINKED 로 전이시킨 뒤 부분 UNIQUE 인덱스를 건다.

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

-- Enforce at DB level that each user can have at most one ACTIVE wallet.
-- UNLINKED / USER_DELETED / BLOCKED / HARD_DELETED 행은 제약 대상 아님.
CREATE UNIQUE INDEX uk_user_wallets_one_active_per_user
    ON user_wallets (user_id)
    WHERE status = 'ACTIVE';
