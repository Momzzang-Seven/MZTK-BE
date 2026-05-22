package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.wallet.WalletApprovalUnavailableException;
import momzzangseven.mztkbe.global.error.wallet.WalletNotFoundException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RetryWalletRegistrationApprovalCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalCapability;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraft;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionRequest;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionWriteView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationReceiptTimeout;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationStatusResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RetryWalletRegistrationApprovalUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.BuildWalletApprovalExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.CancelWalletApprovalExecutionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalCapabilityPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalTtlPolicyPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LockWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.RunWalletRegistrationRetryTransactionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SubmitWalletApprovalExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import org.springframework.stereotype.Service;

/** User-facing retry service for creating a new approval intent without redoing ownership proof. */
@Service
@RequiredArgsConstructor
public class RetryWalletRegistrationApprovalService
    implements RetryWalletRegistrationApprovalUseCase {

  private static final String RETRY_ERROR_CODE = "APPROVAL_RETRY_REQUESTED";
  private static final String RETRY_ERROR_REASON = "approval retry requested";
  private static final String EIP7702_DEADLINE_TOO_CLOSE = "EIP7702_DEADLINE_TOO_CLOSE";
  private static final String ORPHAN_RETRY_INTENT_ERROR_CODE = "APPROVAL_RETRY_ATTACH_FAILED";
  private static final String ORPHAN_RETRY_INTENT_ERROR_REASON = "approval retry attach abandoned";

  private final LockWalletRegistrationSessionPort lockSessionPort;
  private final SaveWalletRegistrationSessionPort saveSessionPort;
  private final LoadWalletApprovalExecutionStatePort loadExecutionStatePort;
  private final LoadWalletApprovalCapabilityPort loadWalletApprovalCapabilityPort;
  private final LoadWalletApprovalTtlPolicyPort loadWalletApprovalTtlPolicyPort;
  private final BuildWalletApprovalExecutionDraftPort buildDraftPort;
  private final SubmitWalletApprovalExecutionDraftPort submitDraftPort;
  private final CancelWalletApprovalExecutionPort cancelExecutionPort;
  private final RunWalletRegistrationRetryTransactionPort transactionPort;
  private final Clock appClock;

  @Override
  public WalletRegistrationStatusResult execute(RetryWalletRegistrationApprovalCommand command) {
    RetryApprovalPreparation preparation = inTransaction(() -> prepareRetry(command));
    if (!preparation.requiresNewIntent()) {
      return preparation.reusableResult();
    }

    WalletApprovalExecutionIntentResult approvalIntent = createApprovalIntent(preparation);
    try {
      return inTransaction(() -> attachCreatedIntent(command, preparation, approvalIntent));
    } catch (RuntimeException exception) {
      cancelOrphanIntent(approvalIntent, exception);
      throw exception;
    }
  }

  private RetryApprovalPreparation prepareRetry(RetryWalletRegistrationApprovalCommand command) {
    WalletRegistrationSession session =
        lockSessionPort
            .lockByPublicIdForUpdate(command.registrationId())
            .orElseThrow(WalletNotFoundException::new);
    if (!session.getUserId().equals(command.requesterUserId())) {
      throw new WalletNotFoundException();
    }

    LocalDateTime now = LocalDateTime.now(appClock);
    Optional<WalletApprovalExecutionStateView> currentState = loadCurrentState(session);
    Optional<RetryApprovalPreparation> receiptTimeoutPreparation =
        prepareReceiptTimeoutRetry(session, currentState, now);
    if (receiptTimeoutPreparation.isPresent()) {
      return receiptTimeoutPreparation.get();
    }
    rejectExpiredSession(session, now);
    if (isReusableSignRequest(session, currentState, now)) {
      return RetryApprovalPreparation.reusable(
          WalletRegistrationStatusResult.from(session, currentState.orElse(null)));
    }

    WalletRegistrationSession retryable = ensureRetryable(session, currentState, now);
    validateApprovalAvailable();
    WalletRegistrationSession prepared =
        retryable == session ? session : saveSessionPort.save(retryable);
    return RetryApprovalPreparation.forCreation(prepared);
  }

  private Optional<RetryApprovalPreparation> prepareReceiptTimeoutRetry(
      WalletRegistrationSession session,
      Optional<WalletApprovalExecutionStateView> currentState,
      LocalDateTime now) {
    if (session.getStatus() != WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN
        || currentState.filter(WalletRegistrationReceiptTimeout::isCurrent).isEmpty()) {
      return Optional.empty();
    }

    WalletRegistrationSession updated =
        WalletRegistrationReceiptTimeout.approvalTtlRemains(session, now)
            ? session.markApprovalRetryable(
                WalletRegistrationReceiptTimeout.ERROR_CODE,
                WalletRegistrationReceiptTimeout.ERROR_REASON,
                now)
            : session.markApprovalFailed(
                WalletRegistrationReceiptTimeout.ERROR_CODE,
                WalletRegistrationReceiptTimeout.ERROR_REASON,
                now);
    WalletRegistrationSession saved = saveSessionPort.save(updated);
    if (saved.getStatus() == WalletRegistrationStatus.APPROVAL_FAILED) {
      return Optional.of(
          RetryApprovalPreparation.reusable(
              WalletRegistrationStatusResult.from(saved, currentState.orElse(null), now)));
    }
    validateApprovalAvailable();
    return Optional.of(RetryApprovalPreparation.forCreation(saved));
  }

  private WalletRegistrationStatusResult attachCreatedIntent(
      RetryWalletRegistrationApprovalCommand command,
      RetryApprovalPreparation preparation,
      WalletApprovalExecutionIntentResult approvalIntent) {
    WalletRegistrationSession session =
        lockSessionPort
            .lockByPublicIdForUpdate(command.registrationId())
            .orElseThrow(WalletNotFoundException::new);
    if (!session.getUserId().equals(command.requesterUserId())) {
      throw new WalletNotFoundException();
    }

    LocalDateTime now = LocalDateTime.now(appClock);
    rejectExpiredSession(session, now);
    rejectStaleRetrySession(session, preparation);
    rejectReusedRetryIntent(preparation, approvalIntent);

    Optional<WalletApprovalExecutionStateView> currentState = loadCurrentState(session);
    WalletRegistrationSession retryable = ensureRetryable(session, currentState, now);
    WalletRegistrationSession updated =
        retryable.attachApprovalIntentPreservingDeadline(
            approvalIntent.executionIntent().id(), now);
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
      WalletRegistrationSession session,
      Optional<WalletApprovalExecutionStateView> currentState,
      LocalDateTime now) {
    if (session.getStatus() == WalletRegistrationStatus.APPROVAL_RETRYABLE) {
      return session;
    }
    if (session.getStatus() == WalletRegistrationStatus.APPROVAL_REQUIRED
        && currentState.map(state -> canMoveRequiredSessionToRetryable(state, now)).orElse(true)) {
      return session.markApprovalRetryable(RETRY_ERROR_CODE, RETRY_ERROR_REASON, now);
    }
    throw new Web3InvalidInputException(
        "wallet registration cannot retry approval from current status");
  }

  private WalletApprovalExecutionIntentResult createApprovalIntent(
      RetryApprovalPreparation preparation) {
    WalletApprovalExecutionDraft draft =
        buildDraftPort.build(
            new WalletApprovalExecutionRequest(
                preparation.registrationId(),
                preparation.requesterUserId(),
                preparation.walletAddress(),
                preparation.sessionDeadline(),
                preparation.nextRetryAttemptNo()));
    rejectDeadlineTooClose(draft.expiresAt(), now());
    try {
      return submitDraftPort.submit(draft);
    } catch (Web3TransferException exception) {
      throw WalletApprovalSponsorLimitMapper.map(exception);
    }
  }

  private boolean isReusableSignRequest(
      WalletRegistrationSession session,
      Optional<WalletApprovalExecutionStateView> currentState,
      LocalDateTime now) {
    return session.getStatus() == WalletRegistrationStatus.APPROVAL_REQUIRED
        && currentState
            .filter(state -> "AWAITING_SIGNATURE".equals(state.executionIntentStatus()))
            .filter(state -> state.signRequest() != null)
            .filter(
                state -> state.expiresAt() == null || isReusableExpiresAt(state.expiresAt(), now))
            .isPresent();
  }

  private boolean canMoveRequiredSessionToRetryable(
      WalletApprovalExecutionStateView state, LocalDateTime now) {
    return EIP7702_DEADLINE_TOO_CLOSE.equals(state.signRequestUnavailableReason())
        || isTerminalExecutionStatus(state.executionIntentStatus())
        || ("AWAITING_SIGNATURE".equals(state.executionIntentStatus())
            && state.expiresAt() != null
            && !isReusableExpiresAt(state.expiresAt(), now));
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

  private void validateApprovalAvailable() {
    WalletApprovalCapability capability = loadWalletApprovalCapabilityPort.load();
    if (!capability.available()) {
      throw new WalletApprovalUnavailableException(capability.reason());
    }
  }

  private boolean isReusableExpiresAt(LocalDateTime expiresAt, LocalDateTime now) {
    if (!expiresAt.isAfter(now)) {
      return false;
    }
    long minimumRemainingSeconds = loadWalletApprovalTtlPolicyPort.load().minimumRemainingSeconds();
    return !expiresAt.isBefore(now.plusSeconds(minimumRemainingSeconds));
  }

  private void rejectDeadlineTooClose(LocalDateTime expiresAt, LocalDateTime now) {
    long minimumRemainingSeconds = loadWalletApprovalTtlPolicyPort.load().minimumRemainingSeconds();
    if (!expiresAt.isAfter(now)) {
      throw new Web3InvalidInputException("wallet registration approval deadline is expired");
    }
    if (expiresAt.isBefore(now.plusSeconds(minimumRemainingSeconds))) {
      throw new Web3InvalidInputException("wallet registration approval deadline is too close");
    }
  }

  private void rejectStaleRetrySession(
      WalletRegistrationSession session, RetryApprovalPreparation preparation) {
    if (!Objects.equals(session.getWalletAddress(), preparation.walletAddress())
        || !Objects.equals(session.getLatestExecutionIntentId(), preparation.previousIntentId())
        || !Objects.equals(session.getRetryCount(), preparation.retryCount())
        || !Objects.equals(session.getApprovalExpiresAt(), preparation.sessionDeadline())) {
      throw new Web3InvalidInputException("wallet registration retry changed before attach");
    }
  }

  private void rejectReusedRetryIntent(
      RetryApprovalPreparation preparation, WalletApprovalExecutionIntentResult approvalIntent) {
    if (Objects.equals(approvalIntent.executionIntent().id(), preparation.previousIntentId())) {
      throw new Web3InvalidInputException("wallet registration retry reused previous intent");
    }
  }

  private void cancelOrphanIntent(
      WalletApprovalExecutionIntentResult approvalIntent, RuntimeException exception) {
    if (approvalIntent.existing()) {
      return;
    }
    try {
      cancelExecutionPort.cancelIfSignable(
          approvalIntent.executionIntent().id(),
          ORPHAN_RETRY_INTENT_ERROR_CODE,
          ORPHAN_RETRY_INTENT_ERROR_REASON);
    } catch (RuntimeException cancelException) {
      exception.addSuppressed(cancelException);
    }
  }

  private <T> T inTransaction(java.util.function.Supplier<T> callback) {
    return transactionPort.execute(callback);
  }

  private LocalDateTime now() {
    return LocalDateTime.now(appClock);
  }

  private record RetryApprovalPreparation(
      String registrationId,
      Long requesterUserId,
      String walletAddress,
      String previousIntentId,
      Integer retryCount,
      LocalDateTime sessionDeadline,
      Integer nextRetryAttemptNo,
      WalletRegistrationStatusResult reusableResult) {

    static RetryApprovalPreparation reusable(WalletRegistrationStatusResult result) {
      return new RetryApprovalPreparation(null, null, null, null, null, null, null, result);
    }

    static RetryApprovalPreparation forCreation(WalletRegistrationSession session) {
      return new RetryApprovalPreparation(
          session.getPublicId(),
          session.getUserId(),
          session.getWalletAddress(),
          session.getLatestExecutionIntentId(),
          session.getRetryCount(),
          session.getApprovalExpiresAt(),
          safeRetryCount(session) + 1,
          null);
    }

    boolean requiresNewIntent() {
      return reusableResult == null;
    }

    private static int safeRetryCount(WalletRegistrationSession session) {
      return session.getRetryCount() == null ? 0 : session.getRetryCount();
    }
  }
}
