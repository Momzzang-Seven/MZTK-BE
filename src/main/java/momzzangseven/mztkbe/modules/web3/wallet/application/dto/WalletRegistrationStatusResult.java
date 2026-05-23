package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;

/** Read model returned by wallet registration status and retry use cases. */
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
        && session.getStatus() == WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN
        && WalletRegistrationReceiptTimeout.isCurrent(executionState)) {
      return WalletRegistrationReceiptTimeout.approvalTtlRemains(session, now)
          ? WalletRegistrationStatus.APPROVAL_RETRYABLE
          : WalletRegistrationStatus.APPROVAL_FAILED;
    }
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
    if (session.getStatus() == WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN
        && WalletRegistrationReceiptTimeout.isCurrent(executionState)) {
      return WalletRegistrationReceiptTimeout.ERROR_CODE;
    }
    return session.getLastErrorCode();
  }

  private static String lastErrorReason(
      WalletRegistrationSession session, WalletApprovalExecutionStateView executionState) {
    if (session.getStatus() == WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN
        && WalletRegistrationReceiptTimeout.isCurrent(executionState)) {
      return WalletRegistrationReceiptTimeout.ERROR_REASON;
    }
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
      case FINALIZATION_FAILED, LOCAL_CONFLICT -> WalletRegistrationNextAction.CONTACT_SUPPORT;
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
}
