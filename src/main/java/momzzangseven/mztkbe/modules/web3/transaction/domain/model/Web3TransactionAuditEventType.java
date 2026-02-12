package momzzangseven.mztkbe.modules.web3.transaction.domain.model;

/** Fixed audit event types for web3_transaction_audits.event_type. */
public enum Web3TransactionAuditEventType {
  PREVALIDATE,
  SIGN,
  BROADCAST,
  RECEIPT_POLL,
  STATE_CHANGE,
  CS_OVERRIDE,
  AUTHORIZATION,
  LIMIT_CHECK
}
