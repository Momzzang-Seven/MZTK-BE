package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;

/**
 * Read model returned by wallet registration status and retry use cases.
 *
 * <p>`supportMessageKey` is the stable FE/i18n contract. `userMessage` is a Korean fallback for
 * clients that do not have localized copy yet.
 */
public record WalletRegistrationStatusResult(
    String registrationId,
    WalletRegistrationStatus status,
    String walletAddress,
    Long registeredWalletId,
    String latestExecutionIntentId,
    String latestExecutionStatus,
    LocalDateTime approvalExpiresAt,
    WalletRegistrationTransactionSummary transaction,
    String lastErrorCode,
    String lastErrorReason,
    String signRequestUnavailableReason,
    WalletRegistrationNextAction nextAction,
    String userMessage,
    String supportMessageKey,
    WalletApprovalExecutionWriteView web3) {

  public static WalletRegistrationStatusResult from(
      WalletRegistrationSession session, WalletApprovalExecutionStateView executionState) {
    return from(session, executionState, null);
  }

  public static WalletRegistrationStatusResult from(
      WalletRegistrationSession session,
      WalletApprovalExecutionStateView executionState,
      LocalDateTime now) {
    WalletRegistrationStatus effectiveStatus = effectiveStatus(session, executionState, now);
    WalletApprovalExecutionWriteView web3 = recoverableWeb3(effectiveStatus, executionState);
    return from(session, effectiveStatus, executionState, web3);
  }

  public static WalletRegistrationStatusResult from(
      WalletRegistrationSession session, WalletApprovalExecutionWriteView web3) {
    return from(session, session.getStatus(), null, web3);
  }

  private static WalletRegistrationStatusResult from(
      WalletRegistrationSession session,
      WalletRegistrationStatus effectiveStatus,
      WalletApprovalExecutionStateView executionState,
      WalletApprovalExecutionWriteView web3) {
    WalletRegistrationTransactionSummary transaction = transactionSummary(session, executionState);
    String latestExecutionStatus = latestExecutionStatus(session, executionState, web3);
    return new WalletRegistrationStatusResult(
        session.getPublicId(),
        effectiveStatus,
        session.getWalletAddress(),
        session.getRegisteredWalletId(),
        session.getLatestExecutionIntentId(),
        latestExecutionStatus,
        session.getApprovalExpiresAt(),
        transaction,
        lastErrorCode(session, executionState),
        lastErrorReason(session, executionState),
        signRequestUnavailableReason(executionState, web3),
        nextAction(effectiveStatus, executionState, web3),
        userMessage(effectiveStatus, executionState, web3),
        supportMessageKey(effectiveStatus, executionState, web3),
        web3);
  }

  private static WalletApprovalExecutionWriteView recoverableWeb3(
      WalletRegistrationStatus effectiveStatus, WalletApprovalExecutionStateView executionState) {
    if (effectiveStatus != WalletRegistrationStatus.APPROVAL_REQUIRED
        || executionState == null
        || (executionState.signRequest() == null
            && executionState.signRequestUnavailableReason() == null)
        || !"AWAITING_SIGNATURE".equals(executionState.executionIntentStatus())) {
      return null;
    }
    return WalletApprovalExecutionWriteView.from(executionState);
  }

  private static WalletRegistrationStatus effectiveStatus(
      WalletRegistrationSession session,
      WalletApprovalExecutionStateView executionState,
      LocalDateTime now) {
    if (now != null
        && session.getStatus().isPreSubmissionExpirable()
        && session.getApprovalExpiresAt() != null
        && !session.getApprovalExpiresAt().isAfter(now)) {
      return WalletRegistrationStatus.EXPIRED;
    }
    return session.getStatus();
  }

  private static String lastErrorCode(
      WalletRegistrationSession session, WalletApprovalExecutionStateView executionState) {
    return session.getLastErrorCode();
  }

  private static String lastErrorReason(
      WalletRegistrationSession session, WalletApprovalExecutionStateView executionState) {
    return session.getLastErrorReason();
  }

  private static String signRequestUnavailableReason(
      WalletApprovalExecutionStateView executionState, WalletApprovalExecutionWriteView web3) {
    if (executionState != null) {
      return executionState.signRequestUnavailableReason();
    }
    if (web3 != null) {
      return web3.signRequestUnavailableReason();
    }
    return null;
  }

  private static WalletRegistrationTransactionSummary transactionSummary(
      WalletRegistrationSession session, WalletApprovalExecutionStateView executionState) {
    WalletRegistrationTransactionSummary fromState =
        WalletRegistrationTransactionSummary.from(executionState);
    if (fromState != null) {
      return fromState;
    }
    if (session.getLatestTransactionId() == null) {
      return null;
    }
    return new WalletRegistrationTransactionSummary(
        session.getLatestTransactionId(), null, session.getLatestTransactionHash());
  }

  private static String latestExecutionStatus(
      WalletRegistrationSession session,
      WalletApprovalExecutionStateView executionState,
      WalletApprovalExecutionWriteView web3) {
    if (executionState != null) {
      return executionState.executionIntentStatus();
    }
    if (web3 != null && web3.executionIntent() != null) {
      return web3.executionIntent().status();
    }
    return session.getLastExecutionStatus();
  }

  private static WalletRegistrationNextAction nextAction(
      WalletRegistrationStatus status,
      WalletApprovalExecutionStateView executionState,
      WalletApprovalExecutionWriteView web3) {
    return switch (status) {
      case APPROVAL_REQUIRED -> nextActionForApprovalRequired(executionState, web3);
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
      WalletApprovalExecutionStateView executionState, WalletApprovalExecutionWriteView web3) {
    if (isSubmitted(executionState)) {
      return WalletRegistrationNextAction.WAIT_FOR_APPROVAL_TRANSACTION;
    }
    if (web3 == null || web3.signRequest() == null) {
      return WalletRegistrationNextAction.RETRY_APPROVAL;
    }
    return WalletRegistrationNextAction.SIGN_APPROVAL;
  }

  private static boolean isSubmitted(WalletApprovalExecutionStateView executionState) {
    if (executionState == null) {
      return false;
    }
    return "SIGNED".equals(executionState.executionIntentStatus())
        || "PENDING_ONCHAIN".equals(executionState.executionIntentStatus())
        || "SIGNED".equals(executionState.transactionStatus())
        || "PENDING".equals(executionState.transactionStatus());
  }

  private static String userMessage(
      WalletRegistrationStatus status,
      WalletApprovalExecutionStateView executionState,
      WalletApprovalExecutionWriteView web3) {
    return switch (status) {
      case APPROVAL_REQUIRED ->
          isSubmitted(executionState)
              ? "승인 거래가 처리 중입니다. 완료될 때까지 다시 서명하지 마세요."
              : web3 != null && web3.signRequest() != null
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
      WalletRegistrationStatus status,
      WalletApprovalExecutionStateView executionState,
      WalletApprovalExecutionWriteView web3) {
    return switch (status) {
      case APPROVAL_REQUIRED ->
          isSubmitted(executionState)
              ? "WALLET_APPROVAL_PENDING"
              : web3 != null && web3.signRequest() != null
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
