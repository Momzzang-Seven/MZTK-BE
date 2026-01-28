package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.util.Map;
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
import momzzangseven.mztkbe.global.error.wallet.WalletBlackListException;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.out.LoadChallengePort;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.out.SaveChallengePort;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.Challenge;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.ChallengePurpose;
import momzzangseven.mztkbe.modules.web3.signature.application.port.out.VerifySignaturePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RegisterWalletUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.DeleteWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.RecordWalletEventPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletEvent;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wallet registration service
 *
 * <p>Implements complete wallet registration flow with signature verification.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RegisterWalletService implements RegisterWalletUseCase {

  private final LoadChallengePort loadChallengePort;
  private final SaveChallengePort saveChallengePort;
  private final VerifySignaturePort verifySignaturePort;
  private final LoadWalletPort loadWalletPort;
  private final SaveWalletPort saveWalletPort;
  private final DeleteWalletPort deleteWalletPort;
  private final RecordWalletEventPort eventPort;
  private final jakarta.persistence.EntityManager entityManager;

  @Override
  public RegisterWalletResult execute(RegisterWalletCommand command) {
    log.info(
        "Registering wallet: userId={}, address={}, nonce={}",
        command.userId(),
        command.walletAddress(),
        command.nonce());

    // 1. Validate command
    command.validate();

    // 2. Load challenge
    Challenge challenge =
        loadChallengePort
            .findByNonceAndPurpose(command.nonce(), ChallengePurpose.WALLET_REGISTRATION)
            .orElseThrow(() -> new ChallengeNotFoundException());

    // 3. Validate challenge status
    validateChallenge(challenge, command);

    // 4. Verify signature
    boolean signatureValid =
        verifySignaturePort.verify(
            challenge.getMessage(), command.nonce(), command.signature(), command.walletAddress());

    if (!signatureValid) {
      log.warn(
          "Invalid signature for wallet registration: nonce={}, address={}, user_id = {}",
          command.nonce(),
          command.walletAddress(),
          command.userId());
      throw new InvalidSignatureException();
    }

    // Check if the user already has ACTIVE wallet
    int existingWalletCount =
        loadWalletPort.countWalletsByUserIdAndStatus(command.userId(), WalletStatus.ACTIVE);
    if (existingWalletCount > 0) {
      log.warn("User already has a wallet: userId={}", command.userId());
      throw new WalletAlreadyLinkedException(command.userId().toString());
    }

    // Check if wallet exists in DB
    Optional<UserWallet> existingWallet =
        loadWalletPort.findByWalletAddress(command.walletAddress());

    UserWallet savedWallet;

    if (existingWallet.isEmpty()) {
      // fresh register
      savedWallet = registerNewWallet(command);
    } else {
      // re-register (UNLINKED or USER_DELETED)
      savedWallet = reRegisterWallet(existingWallet.get(), command);
    }

    // Mark challenge as used
    Challenge usedChallenge = challenge.markAsUsed();
    saveChallengePort.save(usedChallenge);

    log.info(
        "Wallet registered successfully: walletId={}, userId={}, address={}",
        savedWallet.getId(),
        savedWallet.getUserId(),
        savedWallet.getWalletAddress());

    return RegisterWalletResult.from(savedWallet);
  }

  /** Validate challenge state */
  private void validateChallenge(Challenge challenge, RegisterWalletCommand command) {
    // Check if already used
    if (challenge.isUsed()) {
      throw new ChallengeAlreadyUsedException();
    }

    // Check if expired
    if (challenge.isExpired()) {
      // Mark as expired
      Challenge expiredChallenge = challenge.markAsExpired();
      saveChallengePort.save(expiredChallenge);

      throw new ChallengeExpiredException();
    }

    // Check if belongs to user
    if (!challenge.matchesUser(command.userId())) {
      throw new UnauthorizedWalletAccessException();
    }

    // Check if address matches
    if (!challenge.matchesAddress(command.walletAddress())) {
      throw new ChallengeMismatchWalletAddressException();
    }
  }

  /**
   * Register fresh wallet
   *
   * @param command
   * @return
   */
  private UserWallet registerNewWallet(RegisterWalletCommand command) {
    log.info(
        "Registering new wallet: userId={}, address={}", command.userId(), command.walletAddress());

    // 1. create wallet and save
    UserWallet wallet =
        UserWallet.create(command.userId(), command.walletAddress(), java.time.Instant.now());
    UserWallet savedWallet = saveWalletPort.save(wallet);

    // 2. record event
    eventPort.record(
        WalletEvent.registered(
            savedWallet.getWalletAddress(),
            savedWallet.getUserId(),
            Map.of(
                "source", "application",
                "action", "new_registration")));

    return savedWallet;
  }

  /**
   * Re-register a wallet
   *
   * @param existingWallet
   * @param command
   * @return
   */
  private UserWallet reRegisterWallet(UserWallet existingWallet, RegisterWalletCommand command) {
    // 1. Check if the wallet is BLOCKED
    if (existingWallet.getStatus() == WalletStatus.BLOCKED) {
      log.warn(
          "Attempted to register blocked wallet: address={}", existingWallet.getWalletAddress());
      throw new WalletBlackListException(existingWallet.getWalletAddress());
    }

    // 2. Check if the wallet is in UNLINKED or USER_DELETED status, otherwise, the wallet already
    // used by other user.
    if (!existingWallet.canBeReRegistered()) {
      log.error(
          "Unexpected wallet status for re-registration: address={}, status={}",
          existingWallet.getWalletAddress(),
          existingWallet.getStatus());
      throw new WalletAlreadyExistsException(existingWallet.getWalletAddress());
    }

    log.info(
        "Re-registering wallet: address={}, previousUserId={}, previousStatus={}, newUserId={}",
        existingWallet.getWalletAddress(),
        existingWallet.getUserId(),
        existingWallet.getStatus(),
        command.userId());

    // 3. Cache wallet previous status and user id
    Long previousUserId = existingWallet.getUserId();
    WalletStatus previousStatus = existingWallet.getStatus();

    // 5. Delete old record
    deleteWalletPort.deleteById(existingWallet.getId());

    // 5-1. Force flush to execute DELETE immediately before INSERT
    // This prevents unique constraint violation when re-registering the same wallet address
    entityManager.flush();
    log.debug(
        "Flushed DELETE operation for wallet re-registration: address={}",
        existingWallet.getWalletAddress());

    // 6. Record HARD_DELETED event
    eventPort.record(
        WalletEvent.hardDeleted(
            existingWallet.getWalletAddress(),
            previousUserId,
            previousStatus,
            Map.of(
                "source", "application",
                "action", "re_registration_cleanup",
                "new_user_id", command.userId())));

    // 7. INSERT new record into user_wallets
    UserWallet newWallet =
        UserWallet.create(command.userId(), command.walletAddress(), java.time.Instant.now());
    UserWallet savedWallet = saveWalletPort.save(newWallet);

    // 8. Record REGISTERED event
    eventPort.record(
        WalletEvent.reRegistered(
            savedWallet.getWalletAddress(),
            savedWallet.getUserId(),
            previousUserId,
            previousStatus,
            Map.of(
                "source",
                "application",
                "action",
                "re_registration",
                "previous_user_id",
                previousUserId,
                "previous_status",
                previousStatus.name())));

    return savedWallet;
  }
}
