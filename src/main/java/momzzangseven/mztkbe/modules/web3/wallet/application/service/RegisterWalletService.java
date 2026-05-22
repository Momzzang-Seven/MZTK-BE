package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.challenge.ChallengeAlreadyUsedException;
import momzzangseven.mztkbe.global.error.challenge.ChallengeExpiredException;
import momzzangseven.mztkbe.global.error.challenge.ChallengeMismatchWalletAddressException;
import momzzangseven.mztkbe.global.error.challenge.ChallengeNotFoundException;
import momzzangseven.mztkbe.global.error.signature.InvalidSignatureException;
import momzzangseven.mztkbe.global.error.wallet.UnauthorizedWalletAccessException;
import momzzangseven.mztkbe.global.error.wallet.WalletAlreadyExistsException;
import momzzangseven.mztkbe.global.error.wallet.WalletAlreadyLinkedException;
import momzzangseven.mztkbe.global.error.wallet.WalletApprovalUnavailableException;
import momzzangseven.mztkbe.global.error.wallet.WalletBlackListException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ExpireWalletRegistrationSessionCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.MarkWalletRegistrationApprovalTerminatedCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalCapability;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionWriteView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationChallengeView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationDuplicateResolution;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationDuplicateResolutionType;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationReceiptTimeout;
import momzzangseven.mztkbe.modules.web3.wallet.application.exception.DuplicateWalletRegistrationSessionException;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.ExpireWalletRegistrationSessionUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.MarkWalletRegistrationApprovalTerminatedUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RegisterWalletApprovalAttemptUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RegisterWalletUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.AcquireWalletRegistrationAuthorityLockPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalCapabilityPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationChallengePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.MarkWalletRegistrationChallengeExpiredPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.MarkWalletRegistrationChallengeUsedPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.VerifyWalletOwnershipSignaturePort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wallet registration entry service.
 *
 * <p>The service verifies wallet ownership and local eligibility, then creates a pending wallet
 * registration session that requires EIP-7702 approval before the wallet becomes ACTIVE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterWalletService implements RegisterWalletUseCase {

  private final LoadWalletRegistrationChallengePort loadChallengePort;
  private final MarkWalletRegistrationChallengeUsedPort markChallengeUsedPort;
  private final MarkWalletRegistrationChallengeExpiredPort markChallengeExpiredPort;
  private final VerifyWalletOwnershipSignaturePort verifySignaturePort;
  private final LoadWalletPort loadWalletPort;
  private final LoadWalletApprovalCapabilityPort loadWalletApprovalCapabilityPort;
  private final LoadWalletApprovalExecutionStatePort loadWalletApprovalExecutionStatePort;
  private final AcquireWalletRegistrationAuthorityLockPort authorityLockPort;
  private final WalletRegistrationSessionDuplicateResolver duplicateResolver;
  private final RegisterWalletApprovalAttemptUseCase approvalAttemptUseCase;
  private final ExpireWalletRegistrationSessionUseCase expireSessionUseCase;
  private final MarkWalletRegistrationApprovalTerminatedUseCase markTerminatedUseCase;
  private final Clock appClock;

  @Override
  @Transactional
  public RegisterWalletResult execute(RegisterWalletCommand command) {
    log.info(
        "Registering wallet approval session: userId={}, address={}, nonce={}",
        command.userId(),
        command.walletAddress(),
        command.nonce());

    command.validate();
    WalletRegistrationChallengeView challenge = loadWalletRegistrationChallenge(command);
    validateChallenge(challenge, command);
    verifyOwnershipSignature(challenge, command);
    validateApprovalAvailable();
    authorityLockPort.lock(command.userId(), command.walletAddress());
    validateLocalWalletEligibility(command);

    WalletRegistrationDuplicateResolution currentDuplicate =
        resolveCurrentAfterReconciliation(command.userId(), command.walletAddress());
    if (currentDuplicate.shouldReuse()) {
      markChallengeUsed(challenge);
      return toPendingResult(currentDuplicate.session());
    }
    rejectDuplicateConflict(command, currentDuplicate);

    try {
      return approvalAttemptUseCase.createPendingApproval(command);
    } catch (Web3TransferException exception) {
      throw WalletApprovalSponsorLimitMapper.map(exception);
    } catch (DuplicateWalletRegistrationSessionException exception) {
      WalletRegistrationDuplicateResolution raceResolution =
          resolveAfterCreateRaceAfterReconciliation(command.userId(), command.walletAddress());
      if (raceResolution.type() == WalletRegistrationDuplicateResolutionType.CREATE_NEW) {
        return approvalAttemptUseCase.createPendingApproval(command);
      }
      if (raceResolution.shouldReuse()) {
        markChallengeUsed(challenge);
        return toPendingResult(raceResolution.session());
      }
      rejectDuplicateConflict(command, raceResolution);
      throw exception;
    }
  }

  private WalletRegistrationChallengeView loadWalletRegistrationChallenge(
      RegisterWalletCommand command) {
    return loadChallengePort.load(command.nonce()).orElseThrow(ChallengeNotFoundException::new);
  }

  /** Validates ownership challenge state without consuming it. */
  private void validateChallenge(
      WalletRegistrationChallengeView challenge, RegisterWalletCommand command) {
    if (challenge.used()) {
      throw new ChallengeAlreadyUsedException();
    }
    if (challenge.expired()) {
      markChallengeExpiredPort.markExpired(challenge.nonce());
      throw new ChallengeExpiredException();
    }
    if (!challenge.matchesUser(command.userId())) {
      throw new UnauthorizedWalletAccessException();
    }
    if (!challenge.matchesAddress(command.walletAddress())) {
      throw new ChallengeMismatchWalletAddressException();
    }
  }

  private void verifyOwnershipSignature(
      WalletRegistrationChallengeView challenge, RegisterWalletCommand command) {
    boolean signatureValid =
        verifySignaturePort.verify(
            challenge.message(), command.nonce(), command.signature(), command.walletAddress());
    if (!signatureValid) {
      log.warn(
          "Invalid signature for wallet registration: nonce={}, address={}, userId={}",
          command.nonce(),
          command.walletAddress(),
          command.userId());
      throw new InvalidSignatureException();
    }
  }

  private void validateApprovalAvailable() {
    WalletApprovalCapability capability = loadWalletApprovalCapabilityPort.load();
    if (!capability.available()) {
      throw new WalletApprovalUnavailableException(capability.reason());
    }
  }

  private void validateLocalWalletEligibility(RegisterWalletCommand command) {
    int existingWalletCount =
        loadWalletPort.countWalletsByUserIdAndStatus(command.userId(), WalletStatus.ACTIVE);
    if (existingWalletCount > 0) {
      throw new WalletAlreadyLinkedException(command.userId().toString());
    }

    Optional<UserWallet> existingWallet =
        loadWalletPort.findByWalletAddress(command.walletAddress());
    if (existingWallet.isEmpty()) {
      return;
    }

    UserWallet wallet = existingWallet.get();
    if (wallet.getStatus() == WalletStatus.BLOCKED) {
      throw new WalletBlackListException(wallet.getWalletAddress());
    }
    if (!wallet.canBeReRegistered()) {
      throw new WalletAlreadyExistsException(wallet.getWalletAddress());
    }
  }

  private void rejectDuplicateConflict(
      RegisterWalletCommand command, WalletRegistrationDuplicateResolution resolution) {
    if (!resolution.isConflict()) {
      return;
    }
    if (resolution.type() == WalletRegistrationDuplicateResolutionType.USER_HAS_PENDING_SESSION) {
      throw new WalletAlreadyLinkedException(command.userId().toString());
    }
    throw new WalletAlreadyExistsException(command.walletAddress());
  }

  private WalletRegistrationDuplicateResolution resolveCurrentAfterReconciliation(
      Long userId, String walletAddress) {
    WalletRegistrationDuplicateResolution resolution =
        duplicateResolver.resolveCurrent(userId, walletAddress);
    if (reconcileDuplicate(resolution)) {
      return duplicateResolver.resolveCurrent(userId, walletAddress);
    }
    return resolution;
  }

  private WalletRegistrationDuplicateResolution resolveAfterCreateRaceAfterReconciliation(
      Long userId, String walletAddress) {
    WalletRegistrationDuplicateResolution resolution =
        duplicateResolver.resolveAfterCreateRace(userId, walletAddress);
    if (reconcileDuplicate(resolution)) {
      return duplicateResolver.resolveCurrent(userId, walletAddress);
    }
    return resolution;
  }

  private boolean reconcileDuplicate(WalletRegistrationDuplicateResolution resolution) {
    WalletRegistrationSession session = resolution.session();
    if (session == null) {
      return false;
    }
    if (session.getStatus() == WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN
        && session.getLatestExecutionIntentId() != null) {
      Optional<WalletApprovalExecutionStateView> currentState =
          loadWalletApprovalExecutionStatePort.loadByExecutionIntentId(
              session.getUserId(), session.getLatestExecutionIntentId());
      if (currentState.filter(WalletRegistrationReceiptTimeout::isCurrent).isPresent()) {
        markTerminatedUseCase.execute(
            new MarkWalletRegistrationApprovalTerminatedCommand(
                session.getPublicId(),
                session.getLatestExecutionIntentId(),
                WalletRegistrationReceiptTimeout.ERROR_CODE,
                WalletRegistrationReceiptTimeout.ERROR_REASON));
        return true;
      }
    }
    if (!isApprovalTtlElapsed(session)) {
      return false;
    }
    expireSessionUseCase.execute(new ExpireWalletRegistrationSessionCommand(session.getPublicId()));
    return true;
  }

  private boolean isApprovalTtlElapsed(WalletRegistrationSession session) {
    return session.getStatus().isPreSubmissionExpirable()
        && session.getApprovalExpiresAt() != null
        && !session.getApprovalExpiresAt().isAfter(LocalDateTime.now(appClock));
  }

  private RegisterWalletResult toPendingResult(WalletRegistrationSession session) {
    if (session.getStatus() != WalletRegistrationStatus.APPROVAL_REQUIRED
        || session.getLatestExecutionIntentId() == null) {
      return RegisterWalletResult.pending(session, null);
    }
    WalletApprovalExecutionWriteView web3 =
        loadWalletApprovalExecutionStatePort
            .loadByExecutionIntentId(session.getUserId(), session.getLatestExecutionIntentId())
            .filter(this::isRecoverableApprovalState)
            .map(WalletApprovalExecutionWriteView::from)
            .orElse(null);
    return RegisterWalletResult.pending(session, web3);
  }

  private boolean isRecoverableApprovalState(WalletApprovalExecutionStateView state) {
    return "AWAITING_SIGNATURE".equals(state.executionIntentStatus())
        && (state.signRequest() != null || state.signRequestUnavailableReason() != null);
  }

  private void markChallengeUsed(WalletRegistrationChallengeView challenge) {
    markChallengeUsedPort.markUsed(challenge.nonce());
  }
}
