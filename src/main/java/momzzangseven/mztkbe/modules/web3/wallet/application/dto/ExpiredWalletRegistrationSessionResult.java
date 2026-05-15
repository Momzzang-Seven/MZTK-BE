package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

/** Internal result from expiring a wallet registration session. */
public record ExpiredWalletRegistrationSessionResult(
    boolean expired, String canceledExecutionIntentId) {

  public static ExpiredWalletRegistrationSessionResult none() {
    return new ExpiredWalletRegistrationSessionResult(false, null);
  }

  public static ExpiredWalletRegistrationSessionResult expired(String executionIntentId) {
    return new ExpiredWalletRegistrationSessionResult(true, executionIntentId);
  }
}
