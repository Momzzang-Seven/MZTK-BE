ALTER TABLE web3_transaction_audits
    DROP CONSTRAINT IF EXISTS ck_web3_tx_audit_event_type;

ALTER TABLE web3_transaction_audits
    ADD CONSTRAINT ck_web3_tx_audit_event_type CHECK (
        event_type IN (
            'PREVALIDATE',
            'SIGN',
            'BROADCAST',
            'RECEIPT_POLL',
            'STATE_CHANGE',
            'CS_OVERRIDE',
            'REQUEUE',
            'AUTHORIZATION',
            'LIMIT_CHECK'
        )
    );
