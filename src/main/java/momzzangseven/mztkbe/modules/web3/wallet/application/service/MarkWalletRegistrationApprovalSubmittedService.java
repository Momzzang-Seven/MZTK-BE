package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.MarkWalletRegistrationApprovalSubmittedCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.MarkWalletRegistrationApprovalSubmittedUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LockWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Synchronizes wallet registration session state after approval transaction submission. */
@Service
@RequiredArgsConstructor
public class MarkWalletRegistrationApprovalSubmittedService
    implements MarkWalletRegistrationApprovalSubmittedUseCase {

  private static final String SIGNED = "SIGNED";
  private static final String PENDING = "PENDING";

  private final LockWalletRegistrationSessionPort lockSessionPort;
  private final SaveWalletRegistrationSessionPort saveSessionPort;
  private final LoadWalletApprovalExecutionStatePort loadExecutionStatePort;
  private final Clock appClock;

  @Override
  @Transactional
  public void execute(MarkWalletRegistrationApprovalSubmittedCommand command) {
    lockSessionPort
        .lockByPublicIdForUpdate(command.registrationId())
        .ifPresent(session -> markSubmitted(command, session));
  }

  private void markSubmitted(
      MarkWalletRegistrationApprovalSubmittedCommand command, WalletRegistrationSession session) {
    if (!isLatestIntent(session, command.executionIntentId()) || shouldIgnore(session)) {
      return;
    }

    Optional<WalletApprovalExecutionStateView> executionState =
        loadExecutionStatePort.loadByExecutionIntentId(
            session.getUserId(), command.executionIntentId());
    SubmissionView submission = SubmissionView.from(command, executionState);
    if (submission.isPending()) {
      saveSessionPort.save(markPending(session, command.executionIntentId(), submission));
      return;
    }
    if (submission.isSigned()) {
      saveSessionPort.save(markSigned(session, command.executionIntentId(), submission));
    }
  }

  private WalletRegistrationSession markPending(
      WalletRegistrationSession session, String executionIntentId, SubmissionView submission) {
    WalletRegistrationSession signed =
        session.getStatus() == WalletRegistrationStatus.APPROVAL_REQUIRED
            ? markSigned(session, executionIntentId, submission)
            : session;
    if (signed.getStatus() == WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN) {
      return signed;
    }
    return signed.markApprovalPendingOnchain(
        executionIntentId,
        submission.transactionId(),
        submission.txHash(),
        submission.executionStatus(),
        LocalDateTime.now(appClock));
  }

  private WalletRegistrationSession markSigned(
      WalletRegistrationSession session, String executionIntentId, SubmissionView submission) {
    if (session.getStatus() != WalletRegistrationStatus.APPROVAL_REQUIRED) {
      return session;
    }
    return session.markApprovalSigned(
        executionIntentId,
        submission.transactionId(),
        submission.txHash(),
        submission.executionStatus(),
        LocalDateTime.now(appClock));
  }

  private boolean isLatestIntent(WalletRegistrationSession session, String executionIntentId) {
    return session.getLatestExecutionIntentId() != null
        && session.getLatestExecutionIntentId().equals(executionIntentId);
  }

  private boolean shouldIgnore(WalletRegistrationSession session) {
    return session.isTerminal() || session.getStatus().isConfirmedButNotFinalized();
  }

  private record SubmissionView(
      String transactionStatus, String executionStatus, Long transactionId, String txHash) {

    static SubmissionView from(
        MarkWalletRegistrationApprovalSubmittedCommand command,
        Optional<WalletApprovalExecutionStateView> state) {
      String transactionStatus =
          state
              .map(WalletApprovalExecutionStateView::transactionStatus)
              .orElse(command.submittedTransactionStatus());
      String executionStatus =
          state
              .map(WalletApprovalExecutionStateView::executionIntentStatus)
              .orElse(command.submittedTransactionStatus());
      Long transactionId = state.map(WalletApprovalExecutionStateView::transactionId).orElse(null);
      String txHash = state.map(WalletApprovalExecutionStateView::txHash).orElse(null);
      return new SubmissionView(transactionStatus, executionStatus, transactionId, txHash);
    }

    boolean isSigned() {
      return SIGNED.equals(transactionStatus) || SIGNED.equals(executionStatus);
    }

    boolean isPending() {
      return PENDING.equals(transactionStatus) || "PENDING_ONCHAIN".equals(executionStatus);
    }
  }
}
