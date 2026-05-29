CREATE INDEX IF NOT EXISTS idx_class_reservations_web3_auto_settle_scan
    ON class_reservations (
        status,
        escrow_flow,
        escrow_status,
        reservation_date,
        reservation_time,
        id
    )
    WHERE status = 'APPROVED'
      AND escrow_flow = 'USER_EIP7702'
      AND escrow_status = 'LOCKED'
      AND current_execution_intent_public_id IS NULL
      AND order_key IS NOT NULL;
