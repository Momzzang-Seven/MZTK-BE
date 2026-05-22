package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.FinalizeWalletRegistrationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationReceiptTimeout;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.AcquireWalletRegistrationAuthorityLockPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LockWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists post-confirm finalization failure states in an independent transaction. */
@Slf4j
@Service
@RequiredArgsConstructor
class WalletRegistrationFinalizationFailureRecorder {

  static final String FINALIZATION_FAILED = "FINALIZATION_FAILED";

  private final LockWalletRegistrationSessionPort lockSessionPort;
  private final LoadWalletRegistrationSessionPort loadSessionPort;
  private final AcquireWalletRegistrationAuthorityLockPort authorityLockPort;
  private final SaveWalletRegistrationSessionPort saveSessionPort;
  private final Clock appClock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordLocalConflict(
      FinalizeWalletRegistrationCommand command, String errorCode, String errorReason) {
    record(command, errorCode, errorReason, true);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordUnexpectedFailure(
      FinalizeWalletRegistrationCommand command, String errorCode, String errorReason) {
    record(command, errorCode, errorReason, false);
  }

  private void record(
      FinalizeWalletRegistrationCommand command,
      String errorCode,
      String errorReason,
      boolean localConflict) {
    try {
      WalletRegistrationSession authoritySnapshot =
          loadSessionPort.loadByPublicId(command.registrationId()).orElse(null);
      if (authoritySnapshot == null) {
        return;
      }
      authorityLockPort.lock(authoritySnapshot.getUserId(), authoritySnapshot.getWalletAddress());
      lockSessionPort
          .lockByPublicIdForUpdate(command.registrationId())
          .ifPresent(
              session -> saveFailure(command, session, errorCode, errorReason, localConflict));
    } catch (RuntimeException exception) {
      log.error(
          "Failed to persist wallet registration finalization failure: registrationId={}, executionIntentId={}",
          command.registrationId(),
          command.executionIntentId(),
          exception);
    }
  }

  private void saveFailure(
      FinalizeWalletRegistrationCommand command,
      WalletRegistrationSession session,
      String errorCode,
      String errorReason,
      boolean localConflict) {
    if (session.getLatestExecutionIntentId() == null
        || !session.getLatestExecutionIntentId().equals(command.executionIntentId())) {
      return;
    }
    if (session.getStatus() == WalletRegistrationStatus.REGISTERED) {
      return;
    }
    if (session.isTerminal() && !isReceiptTimeoutLateSuccess(session)) {
      return;
    }
    if (!isFinalizationFailureStatus(session)) {
      return;
    }

    LocalDateTime now = LocalDateTime.now(appClock);
    WalletRegistrationSession confirmed =
        session.markApprovalConfirmed(command.executionIntentId(), null, null, "CONFIRMED", now);
    WalletRegistrationSession failed =
        localConflict
            ? confirmed.markLocalConflict(errorCode, errorReason, now)
            : confirmed.markFinalizationFailed(errorCode, errorReason, now);
    saveSessionPort.save(failed);
  }

  private boolean isFinalizationFailureStatus(WalletRegistrationSession session) {
    return session.getStatus() == WalletRegistrationStatus.APPROVAL_REQUIRED
        || session.getStatus() == WalletRegistrationStatus.APPROVAL_SIGNED
        || session.getStatus() == WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN
        || isReceiptTimeoutLateSuccess(session)
        || session.getStatus().isConfirmedButNotFinalized();
  }

  private boolean isReceiptTimeoutLateSuccess(WalletRegistrationSession session) {
    return (session.getStatus() == WalletRegistrationStatus.APPROVAL_RETRYABLE
            || session.getStatus() == WalletRegistrationStatus.APPROVAL_FAILED)
        && WalletRegistrationReceiptTimeout.isRecordedOn(session);
  }
}
