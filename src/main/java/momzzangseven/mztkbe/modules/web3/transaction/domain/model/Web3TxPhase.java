package momzzangseven.mztkbe.modules.web3.transaction.domain.model;

/** FE-friendly grouping of Web3 transaction states. */
public enum Web3TxPhase {
  PENDING,
  SUCCESS,
  FAILED;

  public static Web3TxPhase from(Web3TxStatus status) {
    if (status == Web3TxStatus.SUCCEEDED) {
      return SUCCESS;
    }
    if (status == Web3TxStatus.FAILED_ONCHAIN) {
      return FAILED;
    }
    return PENDING;
  }
}
