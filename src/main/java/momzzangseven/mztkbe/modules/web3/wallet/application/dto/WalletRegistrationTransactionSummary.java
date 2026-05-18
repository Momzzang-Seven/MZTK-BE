package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

/** Latest approval transaction summary exposed through wallet registration status. */
public record WalletRegistrationTransactionSummary(
    Long transactionId, String transactionStatus, String txHash) {

  public static WalletRegistrationTransactionSummary from(WalletApprovalExecutionStateView state) {
    if (state == null || state.transactionId() == null) {
      return null;
    }
    return new WalletRegistrationTransactionSummary(
        state.transactionId(), state.transactionStatus(), state.txHash());
  }
}
