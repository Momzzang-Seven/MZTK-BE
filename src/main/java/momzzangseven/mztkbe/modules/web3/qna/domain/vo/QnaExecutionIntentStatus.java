package momzzangseven.mztkbe.modules.web3.qna.domain.vo;

public enum QnaExecutionIntentStatus {
  AWAITING_SIGNATURE,
  SIGNED,
  PENDING_ONCHAIN,
  CONFIRMED,
  FAILED_ONCHAIN,
  EXPIRED,
  CANCELED,
  NONCE_STALE;

  public boolean isActive() {
    return this == AWAITING_SIGNATURE || this == SIGNED || this == PENDING_ONCHAIN;
  }

  public boolean isConfirmed() {
    return this == CONFIRMED;
  }

  public boolean isTerminal() {
    return this == CONFIRMED
        || this == FAILED_ONCHAIN
        || this == EXPIRED
        || this == CANCELED
        || this == NONCE_STALE;
  }

  public boolean isTerminalFailure() {
    return isTerminal() && this != CONFIRMED;
  }
}
