package momzzangseven.mztkbe.modules.web3.transfer.domain.vo;

public enum TransferExecutionIntentStatus {
  AWAITING_SIGNATURE,
  SIGNED,
  PENDING_ONCHAIN,
  CONFIRMED,
  FAILED_ONCHAIN,
  EXPIRED,
  CANCELED,
  NONCE_STALE
}
