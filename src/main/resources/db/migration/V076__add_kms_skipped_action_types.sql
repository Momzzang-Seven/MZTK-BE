-- PR #177 R1+R2: in-port CAS gate 가 stale event 를 silent skip 할 때 기록하는 audit action.
-- web3_treasury_kms_audits.action_type CHECK 를 재정의해 두 신규 action 을 허용한다.
-- 기존 5종 (KMS_DISABLE, KMS_SCHEDULE_DELETION, KMS_CREATE_ALIAS, KMS_UPDATE_ALIAS,
-- KMS_ENABLE) 은 그대로.
--
-- Idempotent: DROP IF EXISTS + ADD.

ALTER TABLE web3_treasury_kms_audits
    DROP CONSTRAINT IF EXISTS ck_web3_treasury_kms_audits_action;
ALTER TABLE web3_treasury_kms_audits
    ADD CONSTRAINT ck_web3_treasury_kms_audits_action
        CHECK (action_type IN ('KMS_DISABLE', 'KMS_SCHEDULE_DELETION',
                               'KMS_CREATE_ALIAS', 'KMS_UPDATE_ALIAS',
                               'KMS_ENABLE',
                               'KMS_REPLACE_SKIPPED', 'KMS_ENABLE_SKIPPED'));
