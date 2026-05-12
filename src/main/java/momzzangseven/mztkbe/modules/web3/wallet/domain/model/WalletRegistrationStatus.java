package momzzangseven.mztkbe.modules.web3.wallet.domain.model;

/** Lifecycle status for wallet registration sessions that require Web3 approval. */
public enum WalletRegistrationStatus {
  APPROVAL_REQUIRED,
  APPROVAL_SIGNED,
  APPROVAL_PENDING_ONCHAIN,
  APPROVAL_RETRYABLE,
  REGISTERED,
  APPROVAL_FAILED,
  EXPIRED,
  CANCELED,
  FINALIZATION_FAILED,
  LOCAL_CONFLICT;

  /** Returns whether this status should keep user/address duplicate guards active. */
  public boolean isNonTerminal() {
    return switch (this) {
      case APPROVAL_REQUIRED,
              APPROVAL_SIGNED,
              APPROVAL_PENDING_ONCHAIN,
              APPROVAL_RETRYABLE,
              FINALIZATION_FAILED,
              LOCAL_CONFLICT ->
          true;
      case REGISTERED, APPROVAL_FAILED, EXPIRED, CANCELED -> false;
    };
  }

  /** Returns whether session TTL may expire this status. */
  public boolean isPreSubmissionExpirable() {
    return this == APPROVAL_REQUIRED || this == APPROVAL_RETRYABLE;
  }

  /** Returns whether approval was confirmed but local wallet finalization still needs recovery. */
  public boolean isConfirmedButNotFinalized() {
    return this == FINALIZATION_FAILED || this == LOCAL_CONFLICT;
  }
}
