package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ExpireWalletRegistrationSessionCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.FinalizeWalletRegistrationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.MarkWalletRegistrationApprovalSubmittedCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.MarkWalletRegistrationApprovalTerminatedCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ReconcileWalletRegistrationSessionCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ReconcileWalletRegistrationSessionResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RetryWalletRegistrationFinalizationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationReceiptTimeout;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.ExpireWalletRegistrationSessionUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.FinalizeWalletRegistrationUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.MarkWalletRegistrationApprovalSubmittedUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.MarkWalletRegistrationApprovalTerminatedUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.ReconcileWalletRegistrationSessionUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RetryWalletRegistrationFinalizationUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationPolicyPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SyncWalletApprovalExecutionSuccessPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import org.springframework.stereotype.Service;

/** Reconciles one wallet registration session from execution and transaction state. */
@Service
@RequiredArgsConstructor
public class ReconcileWalletRegistrationSessionService
    implements ReconcileWalletRegistrationSessionUseCase {

  private final LoadWalletRegistrationSessionPort loadSessionPort;
  private final LoadWalletApprovalExecutionStatePort loadExecutionStatePort;
  private final MarkWalletRegistrationApprovalSubmittedUseCase markSubmittedUseCase;
  private final MarkWalletRegistrationApprovalTerminatedUseCase markTerminatedUseCase;
  private final FinalizeWalletRegistrationUseCase finalizeUseCase;
  private final RetryWalletRegistrationFinalizationUseCase retryFinalizationUseCase;
  private final ExpireWalletRegistrationSessionUseCase expireUseCase;
  private final SyncWalletApprovalExecutionSuccessPort syncExecutionSuccessPort;
  private final LoadWalletRegistrationPolicyPort registrationPolicyPort;
  private final Clock appClock;

  @Override
  public ReconcileWalletRegistrationSessionResult execute(
      ReconcileWalletRegistrationSessionCommand command) {
    Optional<WalletRegistrationSession> maybeSession =
        loadSessionPort.loadByPublicId(command.registrationId());
    if (maybeSession.isEmpty()) {
      return ReconcileWalletRegistrationSessionResult.skippedResult();
    }

    WalletRegistrationSession session = maybeSession.get();
    if (session.isTerminal() && !isReceiptTimeoutApprovalFailed(session)) {
      return ReconcileWalletRegistrationSessionResult.skippedResult();
    }
    if (session.getStatus().isConfirmedButNotFinalized()) {
      return retryFinalizationIfBackoffElapsed(session);
    }

    Optional<WalletApprovalExecutionStateView> maybeExecutionState = loadExecutionState(session);
    if (maybeExecutionState.isPresent()
        && shouldRecoverExecutionBeforeSessionExpiry(maybeExecutionState.get())) {
      return recoverFromExecutionState(session, maybeExecutionState.get());
    }

    if (shouldExpireSession(session)) {
      boolean expired =
          expireUseCase.execute(new ExpireWalletRegistrationSessionCommand(session.getPublicId()));
      return expired
          ? ReconcileWalletRegistrationSessionResult.recoveredResult()
          : ReconcileWalletRegistrationSessionResult.skippedResult();
    }
    if (maybeExecutionState.isEmpty()) {
      return ReconcileWalletRegistrationSessionResult.skippedResult();
    }

    return recoverFromExecutionState(session, maybeExecutionState.get());
  }

  private ReconcileWalletRegistrationSessionResult recoverFromExecutionState(
      WalletRegistrationSession session, WalletApprovalExecutionStateView executionState) {
    if (isSucceededTransactionBeforeExecutionConfirmed(executionState)) {
      syncExecutionSuccessPort.syncSucceededTransaction(executionState.transactionId());
      return ReconcileWalletRegistrationSessionResult.recoveredResult();
    }
    if ("CONFIRMED".equals(executionState.executionIntentStatus())) {
      finalizeUseCase.execute(
          new FinalizeWalletRegistrationCommand(
              session.getPublicId(), executionState.executionIntentId()));
      return ReconcileWalletRegistrationSessionResult.recoveredResult();
    }
    if (WalletRegistrationReceiptTimeout.isCurrent(executionState)) {
      markTerminatedUseCase.execute(
          new MarkWalletRegistrationApprovalTerminatedCommand(
              session.getPublicId(),
              executionState.executionIntentId(),
              WalletRegistrationReceiptTimeout.ERROR_CODE,
              WalletRegistrationReceiptTimeout.ERROR_REASON));
      return ReconcileWalletRegistrationSessionResult.recoveredResult();
    }
    if (isExpiredSignRequest(executionState) || isTerminalApprovalStatus(executionState)) {
      markTerminatedUseCase.execute(
          new MarkWalletRegistrationApprovalTerminatedCommand(
              session.getPublicId(),
              executionState.executionIntentId(),
              resolvedTerminalStatus(executionState),
              resolvedTerminalReason(executionState)));
      return ReconcileWalletRegistrationSessionResult.recoveredResult();
    }
    if (isSubmitted(executionState)) {
      markSubmittedUseCase.execute(
          new MarkWalletRegistrationApprovalSubmittedCommand(
              session.getPublicId(),
              executionState.executionIntentId(),
              executionState.transactionStatus()));
      return ReconcileWalletRegistrationSessionResult.recoveredResult();
    }
    return ReconcileWalletRegistrationSessionResult.skippedResult();
  }

  private Optional<WalletApprovalExecutionStateView> loadExecutionState(
      WalletRegistrationSession session) {
    if (session.getLatestExecutionIntentId() == null) {
      return Optional.empty();
    }
    return loadExecutionStatePort.loadByExecutionIntentId(
        session.getUserId(), session.getLatestExecutionIntentId());
  }

  private ReconcileWalletRegistrationSessionResult retryFinalizationIfBackoffElapsed(
      WalletRegistrationSession session) {
    LocalDateTime updatedAt = session.getUpdatedAt();
    LocalDateTime now = LocalDateTime.now(appClock);
    int backoffSeconds = registrationPolicyPort.finalizationRetryBackoffSeconds();
    if (updatedAt != null && updatedAt.plusSeconds(backoffSeconds).isAfter(now)) {
      return ReconcileWalletRegistrationSessionResult.skippedResult();
    }
    retryFinalizationUseCase.execute(
        new RetryWalletRegistrationFinalizationCommand(session.getPublicId()));
    return ReconcileWalletRegistrationSessionResult.recoveredResult();
  }

  private boolean shouldExpireSession(WalletRegistrationSession session) {
    return session.getStatus().isPreSubmissionExpirable()
        && session.getApprovalExpiresAt() != null
        && !session.getApprovalExpiresAt().isAfter(LocalDateTime.now(appClock));
  }

  private boolean isReceiptTimeoutApprovalFailed(WalletRegistrationSession session) {
    return session.isTerminal() && WalletRegistrationReceiptTimeout.isRecordedOn(session);
  }

  private boolean isSucceededTransactionBeforeExecutionConfirmed(
      WalletApprovalExecutionStateView executionState) {
    return executionState.transactionId() != null
        && "SUCCEEDED".equals(executionState.transactionStatus())
        && !"CONFIRMED".equals(executionState.executionIntentStatus());
  }

  private boolean shouldRecoverExecutionBeforeSessionExpiry(
      WalletApprovalExecutionStateView executionState) {
    return isSucceededTransactionBeforeExecutionConfirmed(executionState)
        || "CONFIRMED".equals(executionState.executionIntentStatus())
        || WalletRegistrationReceiptTimeout.isCurrent(executionState)
        || isSubmitted(executionState)
        || "FAILED_ONCHAIN".equals(executionState.executionIntentStatus())
        || "FAILED_ONCHAIN".equals(executionState.transactionStatus());
  }

  private boolean isSubmitted(WalletApprovalExecutionStateView executionState) {
    return "SIGNED".equals(executionState.transactionStatus())
        || "PENDING".equals(executionState.transactionStatus())
        || "SIGNED".equals(executionState.executionIntentStatus())
        || "PENDING_ONCHAIN".equals(executionState.executionIntentStatus());
  }

  private boolean isExpiredSignRequest(WalletApprovalExecutionStateView executionState) {
    return "AWAITING_SIGNATURE".equals(executionState.executionIntentStatus())
        && executionState.expiresAt() != null
        && !executionState.expiresAt().isAfter(LocalDateTime.now(appClock));
  }

  private boolean isTerminalApprovalStatus(WalletApprovalExecutionStateView executionState) {
    return "EXPIRED".equals(executionState.executionIntentStatus())
        || "CANCELED".equals(executionState.executionIntentStatus())
        || "NONCE_STALE".equals(executionState.executionIntentStatus())
        || "FAILED_ONCHAIN".equals(executionState.executionIntentStatus())
        || "FAILED_ONCHAIN".equals(executionState.transactionStatus());
  }

  private String resolvedTerminalStatus(WalletApprovalExecutionStateView executionState) {
    if ("FAILED_ONCHAIN".equals(executionState.transactionStatus())) {
      return "FAILED_ONCHAIN";
    }
    if (isExpiredSignRequest(executionState)) {
      return "EXPIRED";
    }
    return executionState.executionIntentStatus();
  }

  private String resolvedTerminalReason(WalletApprovalExecutionStateView executionState) {
    if ("FAILED_ONCHAIN".equals(executionState.transactionStatus())) {
      return "approval transaction failed on-chain";
    }
    return resolvedTerminalStatus(executionState);
  }
}
