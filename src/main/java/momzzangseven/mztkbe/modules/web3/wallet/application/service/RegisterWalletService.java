package momzzangseven.mztkbe.modules.web3.wallet.application.service;

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
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalCapability;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionWriteView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationChallengeView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationDuplicateResolution;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationDuplicateResolutionType;
import momzzangseven.mztkbe.modules.web3.wallet.application.exception.DuplicateWalletRegistrationSessionException;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RegisterWalletUseCase;
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
  private final WalletRegistrationSessionDuplicateResolver duplicateResolver;
  private final RegisterWalletApprovalAttemptService approvalAttemptService;

  @Override
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
    validateLocalWalletEligibility(command);

    WalletRegistrationDuplicateResolution currentDuplicate =
        duplicateResolver.resolveCurrent(command.userId(), command.walletAddress());
    if (currentDuplicate.shouldReuse()) {
      markChallengeUsed(challenge);
      return toPendingResult(currentDuplicate.session());
    }
    rejectDuplicateConflict(command, currentDuplicate);

    try {
      return approvalAttemptService.createPendingApproval(command);
    } catch (DuplicateWalletRegistrationSessionException exception) {
      WalletRegistrationDuplicateResolution raceResolution =
          duplicateResolver.resolveAfterCreateRace(command.userId(), command.walletAddress());
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

  private RegisterWalletResult toPendingResult(WalletRegistrationSession session) {
    if (session.getStatus() != WalletRegistrationStatus.APPROVAL_REQUIRED
        || session.getLatestExecutionIntentId() == null) {
      return RegisterWalletResult.pending(session, null);
    }
    WalletApprovalExecutionWriteView web3 =
        loadWalletApprovalExecutionStatePort
            .loadByExecutionIntentId(session.getUserId(), session.getLatestExecutionIntentId())
            .filter(state -> "AWAITING_SIGNATURE".equals(state.executionIntentStatus()))
            .filter(state -> state.signRequest() != null)
            .map(WalletApprovalExecutionWriteView::from)
            .orElse(null);
    return RegisterWalletResult.pending(session, web3);
  }

  private void markChallengeUsed(WalletRegistrationChallengeView challenge) {
    markChallengeUsedPort.markUsed(challenge.nonce());
  }
}
