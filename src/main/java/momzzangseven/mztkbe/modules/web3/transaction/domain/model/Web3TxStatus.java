package momzzangseven.mztkbe.modules.web3.transaction.domain.model;

/** Canonical Web3 transaction status (SSOT: web3_transactions.status). */
public enum Web3TxStatus {
  CREATED,
  SIGNED,
  PENDING,
  SUCCEEDED,
  FAILED_ONCHAIN,
  UNCONFIRMED;

  public boolean isPendingLike() {
    return this == CREATED || this == SIGNED || this == PENDING || this == UNCONFIRMED;
  }
}
