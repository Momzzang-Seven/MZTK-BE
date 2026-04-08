package momzzangseven.mztkbe.modules.web3.transaction.domain.vo;

public enum TransactionAuditEventType {
  PREVALIDATE,
  SIGN,
  BROADCAST,
  RECEIPT_POLL,
  STATE_CHANGE,
  CS_OVERRIDE,
  AUTHORIZATION,
  LIMIT_CHECK
}
