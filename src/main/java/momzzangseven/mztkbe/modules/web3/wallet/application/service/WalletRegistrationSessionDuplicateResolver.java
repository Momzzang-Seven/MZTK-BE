package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationDuplicateResolution;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Loads current non-terminal sessions and applies duplicate-session policy. */
@Component
@RequiredArgsConstructor
public class WalletRegistrationSessionDuplicateResolver {

  private final LoadWalletRegistrationSessionPort loadSessionPort;
  private final WalletRegistrationSessionDuplicatePolicy duplicatePolicy;

  /** Resolves duplicates before attempting to create a new registration session. */
  @Transactional(readOnly = true)
  public WalletRegistrationDuplicateResolution resolveCurrent(Long userId, String walletAddress) {
    return resolveLoaded(userId, walletAddress);
  }

  /**
   * Resolves duplicates after a create-and-flush race.
   *
   * <p>This method intentionally starts a fresh read-only transaction so callers do not reload the
   * winning row inside the rollback-only transaction that observed the unique violation.
   */
  @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
  public WalletRegistrationDuplicateResolution resolveAfterCreateRace(
      Long userId, String walletAddress) {
    return resolveLoaded(userId, walletAddress);
  }

  private WalletRegistrationDuplicateResolution resolveLoaded(Long userId, String walletAddress) {
    return duplicatePolicy.resolve(
        userId,
        walletAddress,
        loadSessionPort.loadLatestNonTerminalByUserIdAndWalletAddress(userId, walletAddress),
        loadSessionPort.loadLatestNonTerminalByUserId(userId),
        loadSessionPort.loadLatestNonTerminalByWalletAddress(walletAddress));
  }
}
