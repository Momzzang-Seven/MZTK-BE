-- MOM-444: ACTIVATE-from-DISABLED 액션에서 사용할 KMS_ENABLE audit action 도입.
-- web3_treasury_kms_audits.action_type CHECK 를 재정의해 신규 action 을 허용한다.
-- 기존 4종은 그대로.
--
-- Idempotent: DROP IF EXISTS + ADD.

ALTER TABLE web3_treasury_kms_audits
    DROP CONSTRAINT IF EXISTS ck_web3_treasury_kms_audits_action;
ALTER TABLE web3_treasury_kms_audits
    ADD CONSTRAINT ck_web3_treasury_kms_audits_action
        CHECK (action_type IN ('KMS_DISABLE', 'KMS_SCHEDULE_DELETION',
                               'KMS_CREATE_ALIAS', 'KMS_UPDATE_ALIAS',
                               'KMS_ENABLE'));
