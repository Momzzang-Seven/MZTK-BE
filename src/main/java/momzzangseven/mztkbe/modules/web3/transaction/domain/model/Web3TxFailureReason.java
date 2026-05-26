package momzzangseven.mztkbe.modules.web3.transaction.domain.model;

/** Failure reasons used in web3_transactions.failure_reason. */
public enum Web3TxFailureReason {
  RPC_UNAVAILABLE(true),
  TREASURY_ETH_BELOW_CRITICAL(true),
  TREASURY_TOKEN_INSUFFICIENT(false),
  PREVALIDATE_INVALID_COMMAND(false),
  PREVALIDATE_REVERT(false),
  PREVALIDATE_TRANSFER_FALSE(false),
  BROADCAST_FAILED(true),
  BROADCAST_ALREADY_KNOWN(true),
  BROADCAST_NONCE_TOO_LOW(false),
  TREASURY_KEY_MISSING(false),
  TREASURY_WALLET_INACTIVE(false),
  KMS_KEY_NOT_ENABLED(true),
  KMS_DESCRIBE_TERMINAL(false),
  KMS_SIGN_FAILED(true),
  KMS_SIGN_FAILED_TERMINAL(false),
  SIGNATURE_INVALID(false),
  INVALID_SIGNED_TX(false),
  RECEIPT_TIMEOUT(false),
  FROM_ADDRESS_MISMATCH(false),
  SPONSOR_NONCE_WAIT_FOR_OPEN_WINDOW(true),
  SPONSOR_NONCE_RPC_DISAGREEMENT(true),
  SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED(false),
  SPONSOR_NONCE_STALE_RESERVATION(false);

  private final boolean retryable;

  Web3TxFailureReason(boolean retryable) {
    this.retryable = retryable;
  }

  public String code() {
    return name();
  }

  public boolean isRetryable() {
    return retryable;
  }
}
