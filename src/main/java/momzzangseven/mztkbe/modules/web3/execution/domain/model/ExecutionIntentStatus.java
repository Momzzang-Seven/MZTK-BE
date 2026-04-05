package momzzangseven.mztkbe.modules.web3.execution.domain.model;

public enum ExecutionIntentStatus {
  AWAITING_SIGNATURE,
  SIGNED,
  PENDING_ONCHAIN,
  CONFIRMED,
  FAILED_ONCHAIN,
  EXPIRED,
  CANCELED,
  NONCE_STALE;

  public boolean isTerminal() {
    return this == CONFIRMED
        || this == FAILED_ONCHAIN
        || this == EXPIRED
        || this == CANCELED
        || this == NONCE_STALE;
  }

  public boolean isSignable() {
    return this == AWAITING_SIGNATURE;
  }

  public boolean isInFlight() {
    return this == SIGNED || this == PENDING_ONCHAIN;
  }
}
