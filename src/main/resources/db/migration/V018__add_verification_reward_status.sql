ALTER TABLE verification_requests
    ADD COLUMN reward_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN reward_source_ref VARCHAR(255) NULL;

ALTER TABLE verification_requests
    ADD CONSTRAINT chk_verification_reward_status
        CHECK (reward_status IN ('NOT_REQUESTED', 'PENDING', 'SUCCEEDED', 'FAILED'));

UPDATE verification_requests
SET reward_status = 'SUCCEEDED',
    reward_source_ref = CASE verification_kind
        WHEN 'WORKOUT_PHOTO' THEN 'workout-photo-verification:' || verification_id
        WHEN 'WORKOUT_RECORD' THEN 'workout-record-verification:' || verification_id
        ELSE NULL
    END
WHERE status = 'VERIFIED';
