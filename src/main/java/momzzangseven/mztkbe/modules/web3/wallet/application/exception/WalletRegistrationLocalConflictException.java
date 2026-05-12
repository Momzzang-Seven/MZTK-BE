package momzzangseven.mztkbe.modules.web3.wallet.application.exception;

/**
 * Internal exception used when confirmed approval cannot be finalized due to local wallet state.
 */
public class WalletRegistrationLocalConflictException extends RuntimeException {

  private final String errorCode;

  public WalletRegistrationLocalConflictException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public String errorCode() {
    return errorCode;
  }
}
