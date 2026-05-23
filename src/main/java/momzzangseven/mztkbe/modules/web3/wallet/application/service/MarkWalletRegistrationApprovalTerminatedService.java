package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.MarkWalletRegistrationApprovalTerminatedCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationReceiptTimeout;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.MarkWalletRegistrationApprovalTerminatedUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LockWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maps terminal approval execution outcomes to wallet registration session states. */
@Service
@RequiredArgsConstructor
public class MarkWalletRegistrationApprovalTerminatedService
    implements MarkWalletRegistrationApprovalTerminatedUseCase {

  static final String SESSION_EXPIRED_REASON = "WALLET_REGISTRATION_SESSION_EXPIRED";

  private final LockWalletRegistrationSessionPort lockSessionPort;
  private final SaveWalletRegistrationSessionPort saveSessionPort;
  private final Clock appClock;

  @Override
  @Transactional
  public void execute(MarkWalletRegistrationApprovalTerminatedCommand command) {
    lockSessionPort
        .lockByPublicIdForUpdate(command.registrationId())
        .ifPresent(session -> markTerminated(command, session));
  }

  private void markTerminated(
      MarkWalletRegistrationApprovalTerminatedCommand command, WalletRegistrationSession session) {
    if (!isLatestIntent(session, command.executionIntentId()) || shouldIgnore(session)) {
      return;
    }

    LocalDateTime now = LocalDateTime.now(appClock);
    WalletRegistrationSession updated =
        switch (command.terminalExecutionStatus()) {
          case "FAILED_ONCHAIN" -> failOrRetry(session, command.failureReason(), now);
          case WalletRegistrationReceiptTimeout.ERROR_CODE -> receiptTimeout(session, now);
          case "EXPIRED" -> expireOrRetry(session, command.failureReason(), now);
          case "CANCELED", "NONCE_STALE" ->
              session.markApprovalRetryable(
                  command.terminalExecutionStatus(), failureReason(command.failureReason()), now);
          default -> session;
        };
    if (updated != session) {
      saveSessionPort.save(updated);
    }
  }

  private WalletRegistrationSession failOrRetry(
      WalletRegistrationSession session, String failureReason, LocalDateTime now) {
    if (session.getApprovalExpiresAt() != null && session.getApprovalExpiresAt().isAfter(now)) {
      return session.markApprovalRetryable("FAILED_ONCHAIN", failureReason(failureReason), now);
    }
    return session.markApprovalFailed("APPROVAL_FAILED", failureReason(failureReason), now);
  }

  private WalletRegistrationSession expireOrRetry(
      WalletRegistrationSession session, String failureReason, LocalDateTime now) {
    if (SESSION_EXPIRED_REASON.equals(failureReason)
        && session.getStatus().isPreSubmissionExpirable()) {
      return session.expire(SESSION_EXPIRED_REASON, failureReason(failureReason), now);
    }
    if (!session.getStatus().isPreSubmissionExpirable()) {
      return session;
    }
    return session.markApprovalRetryable("EXPIRED", failureReason(failureReason), now);
  }

  private WalletRegistrationSession receiptTimeout(
      WalletRegistrationSession session, LocalDateTime now) {
    if (WalletRegistrationReceiptTimeout.approvalTtlRemains(session, now)) {
      return session.markApprovalRetryable(
          WalletRegistrationReceiptTimeout.ERROR_CODE,
          WalletRegistrationReceiptTimeout.ERROR_REASON,
          now);
    }
    return session.markApprovalFailed(
        WalletRegistrationReceiptTimeout.ERROR_CODE,
        WalletRegistrationReceiptTimeout.ERROR_REASON,
        now);
  }

  private boolean isLatestIntent(WalletRegistrationSession session, String executionIntentId) {
    return session.getLatestExecutionIntentId() != null
        && session.getLatestExecutionIntentId().equals(executionIntentId);
  }

  private boolean shouldIgnore(WalletRegistrationSession session) {
    return session.isTerminal()
        || session.getStatus().isConfirmedButNotFinalized()
        || session.getStatus() == WalletRegistrationStatus.REGISTERED;
  }

  private String failureReason(String failureReason) {
    return failureReason == null || failureReason.isBlank()
        ? "approval execution terminated"
        : failureReason;
  }
}
