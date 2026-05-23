package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

/** Result of reconciling one wallet registration session. */
public record ReconcileWalletRegistrationSessionResult(boolean recovered, boolean skipped) {

  public static ReconcileWalletRegistrationSessionResult recoveredResult() {
    return new ReconcileWalletRegistrationSessionResult(true, false);
  }

  public static ReconcileWalletRegistrationSessionResult skippedResult() {
    return new ReconcileWalletRegistrationSessionResult(false, true);
  }
}
