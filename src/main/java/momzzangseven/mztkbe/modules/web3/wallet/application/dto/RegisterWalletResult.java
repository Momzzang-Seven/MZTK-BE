package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import java.time.Instant;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;

public record RegisterWalletResult(
    String registrationId,
    WalletRegistrationStatus status,
    Long walletId,
    String walletAddress,
    Instant registeredAt,
    WalletRegistrationNextAction nextAction,
    WalletApprovalExecutionWriteView web3) {

  public static RegisterWalletResult pending(
      WalletRegistrationSession session, WalletApprovalExecutionWriteView web3) {
    return new RegisterWalletResult(
        session.getPublicId(),
        session.getStatus(),
        null,
        session.getWalletAddress(),
        null,
        nextAction(session.getStatus(), web3),
        web3);
  }

  public static RegisterWalletResult from(UserWallet wallet) {
    return new RegisterWalletResult(
        null,
        WalletRegistrationStatus.REGISTERED,
        wallet.getId(),
        wallet.getWalletAddress(),
        wallet.getRegisteredAt(),
        WalletRegistrationNextAction.DONE,
        null);
  }

  private static WalletRegistrationNextAction nextAction(
      WalletRegistrationStatus status, WalletApprovalExecutionWriteView web3) {
    return switch (status) {
      case APPROVAL_REQUIRED -> nextActionForApprovalRequired(web3);
      case APPROVAL_SIGNED, APPROVAL_PENDING_ONCHAIN ->
          WalletRegistrationNextAction.WAIT_FOR_APPROVAL_TRANSACTION;
      case APPROVAL_RETRYABLE -> WalletRegistrationNextAction.RETRY_APPROVAL;
      case REGISTERED -> WalletRegistrationNextAction.DONE;
      case FINALIZATION_FAILED, LOCAL_CONFLICT -> WalletRegistrationNextAction.CONTACT_SUPPORT;
      case APPROVAL_FAILED, EXPIRED, CANCELED -> WalletRegistrationNextAction.NONE;
    };
  }

  private static WalletRegistrationNextAction nextActionForApprovalRequired(
      WalletApprovalExecutionWriteView web3) {
    if (web3 != null && web3.signRequest() != null) {
      return WalletRegistrationNextAction.SIGN_APPROVAL;
    }
    return WalletRegistrationNextAction.RETRY_APPROVAL;
  }
}
