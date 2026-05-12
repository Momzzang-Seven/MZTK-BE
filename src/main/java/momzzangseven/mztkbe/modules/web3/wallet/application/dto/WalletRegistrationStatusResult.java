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
    WalletRegistrationNextAction nextAction,
    WalletApprovalExecutionWriteView web3) {

  public static WalletRegistrationStatusResult from(
      WalletRegistrationSession session, WalletApprovalExecutionStateView executionState) {
    WalletApprovalExecutionWriteView web3 = recoverableWeb3(session, executionState);
    return from(session, executionState, web3);
  }

  public static WalletRegistrationStatusResult from(
      WalletRegistrationSession session, WalletApprovalExecutionWriteView web3) {
    return from(session, null, web3);
  }

  private static WalletRegistrationStatusResult from(
      WalletRegistrationSession session,
      WalletApprovalExecutionStateView executionState,
      WalletApprovalExecutionWriteView web3) {
    WalletRegistrationTransactionSummary transaction = transactionSummary(session, executionState);
    String latestExecutionStatus = latestExecutionStatus(session, executionState, web3);
    return new WalletRegistrationStatusResult(
        session.getPublicId(),
        session.getStatus(),
        session.getWalletAddress(),
        session.getRegisteredWalletId(),
        session.getLatestExecutionIntentId(),
        latestExecutionStatus,
        session.getApprovalExpiresAt(),
        transaction,
        session.getLastErrorCode(),
        session.getLastErrorReason(),
        nextAction(session.getStatus(), web3),
        web3);
  }

  private static WalletApprovalExecutionWriteView recoverableWeb3(
      WalletRegistrationSession session, WalletApprovalExecutionStateView executionState) {
    if (session.getStatus() != WalletRegistrationStatus.APPROVAL_REQUIRED
        || executionState == null
        || executionState.signRequest() == null
        || !"AWAITING_SIGNATURE".equals(executionState.executionIntentStatus())) {
      return null;
    }
    return WalletApprovalExecutionWriteView.from(executionState);
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
      WalletRegistrationStatus status, WalletApprovalExecutionWriteView web3) {
    return switch (status) {
      case APPROVAL_REQUIRED ->
          web3 == null
              ? WalletRegistrationNextAction.RETRY_APPROVAL
              : WalletRegistrationNextAction.SIGN_APPROVAL;
      case APPROVAL_SIGNED, APPROVAL_PENDING_ONCHAIN ->
          WalletRegistrationNextAction.WAIT_FOR_APPROVAL_TRANSACTION;
      case APPROVAL_RETRYABLE -> WalletRegistrationNextAction.RETRY_APPROVAL;
      case REGISTERED -> WalletRegistrationNextAction.DONE;
      case FINALIZATION_FAILED, LOCAL_CONFLICT -> WalletRegistrationNextAction.CONTACT_SUPPORT;
      case APPROVAL_FAILED, EXPIRED, CANCELED -> WalletRegistrationNextAction.NONE;
    };
  }
}
