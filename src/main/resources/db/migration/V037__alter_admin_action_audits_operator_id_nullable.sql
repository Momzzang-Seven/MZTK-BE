-- Allow operator_id to be NULL for recovery-path audit records (no JWT principal).
ALTER TABLE admin_action_audits ALTER COLUMN operator_id DROP NOT NULL;
