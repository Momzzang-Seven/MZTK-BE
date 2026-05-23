package momzzangseven.mztkbe.modules.web3.wallet.domain.vo;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public final class WalletApprovalRootIdempotencyKeyFactory {

  private WalletApprovalRootIdempotencyKeyFactory() {}

  public static String createForRegistration(String registrationId) {
    if (registrationId == null || registrationId.isBlank()) {
      throw new Web3InvalidInputException("registrationId is required");
    }
    return "wallet-registration-approval:" + registrationId.trim();
  }

  public static String createForRegistrationRetry(String registrationId, int retryAttemptNo) {
    if (retryAttemptNo <= 0) {
      throw new Web3InvalidInputException("retryAttemptNo must be positive");
    }
    return createForRegistration(registrationId) + ":retry:" + retryAttemptNo;
  }
}
