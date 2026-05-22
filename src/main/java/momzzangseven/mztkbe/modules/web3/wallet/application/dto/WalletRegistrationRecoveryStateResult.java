package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;

/** Owner-agnostic state used by admin/support wallet registration recovery responses. */
public record WalletRegistrationRecoveryStateResult(
    String registrationId,
    Long userId,
    String walletAddress,
    String status,
    String latestExecutionIntentId,
    Long latestTransactionId,
    String latestTransactionHash,
    String lastErrorCode,
    String lastErrorReason,
    boolean newerWalletRegistrationExists,
    Long registeredWalletId) {

  public static WalletRegistrationRecoveryStateResult from(
      WalletRegistrationSession session, boolean newerWalletRegistrationExists) {
    return new WalletRegistrationRecoveryStateResult(
        session.getPublicId(),
        session.getUserId(),
        session.getWalletAddress(),
        session.getStatus().name(),
        session.getLatestExecutionIntentId(),
        session.getLatestTransactionId(),
        session.getLatestTransactionHash(),
        session.getLastErrorCode(),
        session.getLastErrorReason(),
        newerWalletRegistrationExists,
        session.getRegisteredWalletId());
  }
}
