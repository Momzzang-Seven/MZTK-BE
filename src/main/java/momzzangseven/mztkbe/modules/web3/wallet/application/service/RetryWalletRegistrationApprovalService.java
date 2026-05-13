package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.wallet.WalletNotFoundException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RetryWalletRegistrationApprovalCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraft;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionRequest;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionWriteView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationStatusResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RetryWalletRegistrationApprovalUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.BuildWalletApprovalExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LockWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SubmitWalletApprovalExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User-facing retry service for creating a new approval intent without redoing ownership proof. */
@Service
@RequiredArgsConstructor
public class RetryWalletRegistrationApprovalService
    implements RetryWalletRegistrationApprovalUseCase {

  private static final String RETRY_ERROR_CODE = "APPROVAL_RETRY_REQUESTED";
  private static final String RETRY_ERROR_REASON = "approval retry requested";
  private static final String EIP7702_DEADLINE_TOO_CLOSE = "EIP7702_DEADLINE_TOO_CLOSE";

  private final LockWalletRegistrationSessionPort lockSessionPort;
  private final SaveWalletRegistrationSessionPort saveSessionPort;
  private final LoadWalletApprovalExecutionStatePort loadExecutionStatePort;
  private final BuildWalletApprovalExecutionDraftPort buildDraftPort;
  private final SubmitWalletApprovalExecutionDraftPort submitDraftPort;
  private final Clock appClock;

  @Override
  @Transactional
  public WalletRegistrationStatusResult execute(RetryWalletRegistrationApprovalCommand command) {
    WalletRegistrationSession session =
        lockSessionPort
            .lockByPublicIdForUpdate(command.registrationId())
            .orElseThrow(WalletNotFoundException::new);
    if (!session.getUserId().equals(command.requesterUserId())) {
      throw new WalletNotFoundException();
    }

    LocalDateTime now = LocalDateTime.now(appClock);
    rejectExpiredSession(session, now);

    Optional<WalletApprovalExecutionStateView> currentState = loadCurrentState(session);
    if (isReusableSignRequest(session, currentState, now)) {
      return WalletRegistrationStatusResult.from(session, currentState.orElse(null));
    }

    WalletRegistrationSession retryable = ensureRetryable(session, currentState);
    WalletApprovalExecutionIntentResult approvalIntent = createApprovalIntent(retryable);
    WalletRegistrationSession updated =
        retryable.attachApprovalIntent(
            approvalIntent.executionIntent().id(), retryable.getApprovalExpiresAt(), now);
    WalletRegistrationSession saved = saveSessionPort.save(updated);
    return WalletRegistrationStatusResult.from(
        saved, WalletApprovalExecutionWriteView.from(approvalIntent));
  }

  private Optional<WalletApprovalExecutionStateView> loadCurrentState(
      WalletRegistrationSession session) {
    if (session.getLatestExecutionIntentId() == null) {
      return Optional.empty();
    }
    return loadExecutionStatePort.loadByExecutionIntentId(
        session.getUserId(), session.getLatestExecutionIntentId());
  }

  private WalletRegistrationSession ensureRetryable(
      WalletRegistrationSession session, Optional<WalletApprovalExecutionStateView> currentState) {
    if (session.getStatus() == WalletRegistrationStatus.APPROVAL_RETRYABLE) {
      return session;
    }
    if (session.getStatus() == WalletRegistrationStatus.APPROVAL_REQUIRED
        && currentState.map(this::canMoveRequiredSessionToRetryable).orElse(true)) {
      return session.markApprovalRetryable(RETRY_ERROR_CODE, RETRY_ERROR_REASON, now());
    }
    throw new Web3InvalidInputException(
        "wallet registration cannot retry approval from current status");
  }

  private WalletApprovalExecutionIntentResult createApprovalIntent(
      WalletRegistrationSession session) {
    WalletApprovalExecutionDraft draft =
        buildDraftPort.build(
            new WalletApprovalExecutionRequest(
                session.getPublicId(), session.getUserId(), session.getWalletAddress()));
    return submitDraftPort.submit(draft);
  }

  private boolean isReusableSignRequest(
      WalletRegistrationSession session,
      Optional<WalletApprovalExecutionStateView> currentState,
      LocalDateTime now) {
    return session.getStatus() == WalletRegistrationStatus.APPROVAL_REQUIRED
        && currentState
            .filter(state -> "AWAITING_SIGNATURE".equals(state.executionIntentStatus()))
            .filter(state -> state.signRequest() != null)
            .filter(state -> state.expiresAt() == null || state.expiresAt().isAfter(now))
            .isPresent();
  }

  private boolean canMoveRequiredSessionToRetryable(WalletApprovalExecutionStateView state) {
    return EIP7702_DEADLINE_TOO_CLOSE.equals(state.signRequestUnavailableReason())
        || isTerminalExecutionStatus(state.executionIntentStatus())
        || ("AWAITING_SIGNATURE".equals(state.executionIntentStatus())
            && state.expiresAt() != null
            && !state.expiresAt().isAfter(now()));
  }

  private boolean isTerminalExecutionStatus(String status) {
    return "EXPIRED".equals(status)
        || "CANCELED".equals(status)
        || "FAILED_ONCHAIN".equals(status)
        || "NONCE_STALE".equals(status);
  }

  private void rejectExpiredSession(WalletRegistrationSession session, LocalDateTime now) {
    if (session.getApprovalExpiresAt() != null && !session.getApprovalExpiresAt().isAfter(now)) {
      throw new Web3InvalidInputException("wallet registration session is expired");
    }
  }

  private LocalDateTime now() {
    return LocalDateTime.now(appClock);
  }
}
