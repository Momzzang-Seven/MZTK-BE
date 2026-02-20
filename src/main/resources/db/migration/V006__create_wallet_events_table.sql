-- ============================================================
-- wallet_events 테이블 생성 (이벤트 히스토리)
-- Application Layer에서 명시적으로 INSERT
-- ============================================================
CREATE TABLE IF NOT EXISTS wallet_events (
    id BIGSERIAL PRIMARY KEY,
    
    -- 지갑 정보
    wallet_address VARCHAR(42) NOT NULL,
    
    -- 이벤트 정보
    event_type VARCHAR(20) NOT NULL,
    
    -- 소유권 추적
    user_id BIGINT NOT NULL,
    previous_user_id BIGINT NULL,
    
    -- 상태 변경 추적
    previous_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NULL,
    
    -- 메타데이터 (JSON 형식의 문자열)
    metadata TEXT NULL,
    
    -- 타임스탬프
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_wallet_event_type 
        CHECK (event_type IN ('REGISTERED', 'UNLINKED', 'HARD_DELETED', 'USER_DELETED', 'BLOCKED'))
);

-- ============================================================
-- Indexes
-- ============================================================
CREATE INDEX idx_wallet_events_address 
    ON wallet_events(wallet_address);

CREATE INDEX idx_wallet_events_user_id 
    ON wallet_events(user_id);

CREATE INDEX idx_wallet_events_occurred_at 
    ON wallet_events(occurred_at DESC);

CREATE INDEX idx_wallet_events_event_type 
    ON wallet_events(event_type);

CREATE INDEX idx_wallet_events_address_occurred 
    ON wallet_events(wallet_address, occurred_at DESC);

-- ============================================================
-- Comments
-- ============================================================
COMMENT ON TABLE wallet_events IS '지갑 이벤트 히스토리 (Append-Only, Application에서 명시적 INSERT)';
COMMENT ON COLUMN wallet_events.event_type IS 'REGISTERED: 등록, UNLINKED: 해제, HARD_DELETED: 물리삭제, USER_DELETED: 회원탈퇴, BLOCKED: 차단';
COMMENT ON COLUMN wallet_events.previous_user_id IS '이전 소유자 ID (재등록 시 소유권 이전 추적)';
COMMENT ON COLUMN wallet_events.metadata IS 'JSON 형식의 문자열 (예: {"reason": "Scheduled cleanup"})';
