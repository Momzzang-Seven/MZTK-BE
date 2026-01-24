package momzzangseven.mztkbe.modules.web3.wallet.application.service;

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
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
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

    // 5. Check wallet status is blacklisted or not
    if (loadWalletPort.getWalletStatus(command.walletAddress()).equals(WalletStatus.BLACKLISTED)) {
      log.warn("Requested wallet is in blacklist: address = {}", command.walletAddress());
      throw new WalletBlackListException(command.walletAddress());
    }

    // 5. Check wallet duplication
    if (loadWalletPort.existsByWalletAddress(command.walletAddress())) {
      log.warn("Wallet already linked: address={}", command.walletAddress());
      throw new WalletAlreadyLinkedException(command.walletAddress());
    }

    // 6. Check user wallet limit (one wallet per user)
    int existingWalletCount = loadWalletPort.countActiveWalletsByUserId(command.userId());
    if (existingWalletCount > 0) {
      log.warn("User already has a wallet: userId={}", command.userId());
      throw new WalletAlreadyExistsException(command.userId().toString());
    }

    // 7. Mark challenge as used
    Challenge usedChallenge = challenge.markAsUsed();
    saveChallengePort.save(usedChallenge);

    // 8. Create and save wallet
    UserWallet wallet =
        UserWallet.create(command.userId(), command.walletAddress(), java.time.Instant.now());
    UserWallet savedWallet = saveWalletPort.save(wallet);

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
}
