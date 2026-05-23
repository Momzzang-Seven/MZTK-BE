package momzzangseven.mztkbe.modules.web3.wallet.application.exception;

/** Signals that DB duplicate guards rejected a non-terminal wallet registration session. */
public class DuplicateWalletRegistrationSessionException extends RuntimeException {

  private final Long userId;
  private final String walletAddress;

  public DuplicateWalletRegistrationSessionException(
      Long userId, String walletAddress, Throwable cause) {
    super("duplicate wallet registration session", cause);
    this.userId = userId;
    this.walletAddress = walletAddress;
  }

  public Long getUserId() {
    return userId;
  }

  public String getWalletAddress() {
    return walletAddress;
  }
}
