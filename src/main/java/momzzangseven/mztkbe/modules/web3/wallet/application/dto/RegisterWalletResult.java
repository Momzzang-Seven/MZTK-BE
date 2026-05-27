package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import java.time.Instant;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;

/**
 * User-facing wallet registration result.
 *
 * <p>`supportMessageKey` is the stable FE/i18n contract. `userMessage` is a Korean fallback for
 * clients that do not have localized copy yet.
 */
public record RegisterWalletResult(
    String registrationId,
    WalletRegistrationStatus status,
    Long walletId,
    String walletAddress,
    Instant registeredAt,
    WalletRegistrationNextAction nextAction,
    String userMessage,
    String supportMessageKey,
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
        userMessage(session.getStatus(), web3),
        supportMessageKey(session.getStatus(), web3),
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
        userMessage(WalletRegistrationStatus.REGISTERED, null),
        supportMessageKey(WalletRegistrationStatus.REGISTERED, null),
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
      case SPONSOR_NONCE_BLOCKED, FINALIZATION_FAILED, LOCAL_CONFLICT ->
          WalletRegistrationNextAction.CONTACT_SUPPORT;
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

  private static String userMessage(
      WalletRegistrationStatus status, WalletApprovalExecutionWriteView web3) {
    return switch (status) {
      case APPROVAL_REQUIRED ->
          web3 != null && web3.signRequest() != null
              ? "지갑에서 승인 요청에 서명해 주세요."
              : "승인 요청을 다시 준비해야 합니다. 잠시 후 다시 시도해 주세요.";
      case APPROVAL_SIGNED, APPROVAL_PENDING_ONCHAIN -> "승인 거래가 처리 중입니다. 완료될 때까지 다시 서명하지 마세요.";
      case APPROVAL_RETRYABLE -> "승인 요청을 다시 시도할 수 있습니다.";
      case SPONSOR_NONCE_BLOCKED -> "승인 거래 확인이 지연되고 있어 운영자가 확인 중입니다. 다시 서명하지 마세요.";
      case FINALIZATION_FAILED, LOCAL_CONFLICT -> "승인은 확인됐지만 지갑 등록 마무리에 운영자 확인이 필요합니다.";
      case REGISTERED -> "지갑 등록이 완료되었습니다.";
      case APPROVAL_FAILED -> "승인 거래가 실패했습니다. 잠시 후 다시 시도해 주세요.";
      case EXPIRED -> "승인 요청 시간이 만료되었습니다. 지갑 등록을 다시 시작해 주세요.";
      case CANCELED -> "지갑 등록이 취소되었습니다.";
    };
  }

  private static String supportMessageKey(
      WalletRegistrationStatus status, WalletApprovalExecutionWriteView web3) {
    return switch (status) {
      case APPROVAL_REQUIRED ->
          web3 != null && web3.signRequest() != null
              ? "WALLET_APPROVAL_SIGN_REQUEST"
              : "WALLET_APPROVAL_SIGN_REQUEST_UNAVAILABLE";
      case APPROVAL_SIGNED, APPROVAL_PENDING_ONCHAIN -> "WALLET_APPROVAL_PENDING";
      case APPROVAL_RETRYABLE -> "WALLET_APPROVAL_RETRYABLE";
      case SPONSOR_NONCE_BLOCKED -> "WALLET_APPROVAL_OPERATOR_REVIEW";
      case FINALIZATION_FAILED, LOCAL_CONFLICT -> "WALLET_FINALIZATION_OPERATOR_REVIEW";
      case REGISTERED -> "WALLET_REGISTERED";
      case APPROVAL_FAILED -> "WALLET_APPROVAL_FAILED";
      case EXPIRED -> "WALLET_APPROVAL_EXPIRED";
      case CANCELED -> "WALLET_REGISTRATION_CANCELED";
    };
  }
}
