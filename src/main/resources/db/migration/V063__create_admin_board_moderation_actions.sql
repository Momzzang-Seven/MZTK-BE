CREATE TABLE admin_board_moderation_actions (
    id BIGSERIAL PRIMARY KEY,
    operator_id BIGINT NOT NULL REFERENCES users(id),
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    post_id BIGINT NULL,
    board_type VARCHAR(20) NULL,
    reason_code VARCHAR(40) NOT NULL,
    reason_detail VARCHAR(500) NULL,
    target_flow_type VARCHAR(30) NOT NULL DEFAULT 'STANDARD',
    execution_mode VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_admin_board_moderation_target_type
        CHECK (target_type IN ('POST', 'COMMENT')),
    CONSTRAINT ck_admin_board_moderation_board_type
        CHECK (board_type IS NULL OR board_type IN ('FREE', 'QUESTION')),
    CONSTRAINT ck_admin_board_moderation_reason_code
        CHECK (reason_code IN ('INAPPROPRIATE', 'SPAM', 'POLICY_VIOLATION', 'HARASSMENT', 'OTHER')),
    CONSTRAINT ck_admin_board_moderation_target_flow_type
        CHECK (target_flow_type IN ('STANDARD')),
    CONSTRAINT ck_admin_board_moderation_execution_mode
        CHECK (execution_mode IN ('HARD_DELETE', 'SOFT_DELETE', 'UNKNOWN'))
);

ALTER TABLE admin_board_moderation_actions
    ADD CONSTRAINT uk_admin_board_moderation_actions_target
        UNIQUE (target_type, target_id);

CREATE INDEX idx_admin_board_moderation_actions_target
    ON admin_board_moderation_actions(target_type, target_id, created_at DESC);

CREATE INDEX idx_admin_board_moderation_actions_reason
    ON admin_board_moderation_actions(reason_code, created_at DESC);

CREATE INDEX idx_admin_board_moderation_actions_board_type
    ON admin_board_moderation_actions(board_type, created_at DESC);
