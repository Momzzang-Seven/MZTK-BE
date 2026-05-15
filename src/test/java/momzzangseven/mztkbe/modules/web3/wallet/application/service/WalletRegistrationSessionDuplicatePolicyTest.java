package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationDuplicateResolution;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationDuplicateResolutionType;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import org.junit.jupiter.api.Test;

class WalletRegistrationSessionDuplicatePolicyTest {

  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-13T10:00:00");
  private static final String WALLET_ADDRESS = "0x" + "a".repeat(40);

  private final WalletRegistrationSessionDuplicatePolicy policy =
      new WalletRegistrationSessionDuplicatePolicy();

  @Test
  void exactSameUserAndWallet_reusesExistingSession() {
    WalletRegistrationSession existing = session(1L, WALLET_ADDRESS);

    WalletRegistrationDuplicateResolution resolution =
        policy.resolve(
            1L, WALLET_ADDRESS, Optional.of(existing), Optional.empty(), Optional.empty());

    assertThat(resolution.type())
        .isEqualTo(WalletRegistrationDuplicateResolutionType.REUSE_EXISTING);
    assertThat(resolution.shouldReuse()).isTrue();
    assertThat(resolution.session()).isSameAs(existing);
  }

  @Test
  void sameUserWithDifferentWallet_isConflict() {
    WalletRegistrationSession byUser = session(1L, "0x" + "b".repeat(40));

    WalletRegistrationDuplicateResolution resolution =
        policy.resolve(1L, WALLET_ADDRESS, Optional.empty(), Optional.of(byUser), Optional.empty());

    assertThat(resolution.type())
        .isEqualTo(WalletRegistrationDuplicateResolutionType.USER_HAS_PENDING_SESSION);
    assertThat(resolution.isConflict()).isTrue();
  }

  @Test
  void sameWalletWithDifferentUser_isConflict() {
    WalletRegistrationSession byWallet = session(2L, WALLET_ADDRESS);

    WalletRegistrationDuplicateResolution resolution =
        policy.resolve(
            1L, WALLET_ADDRESS, Optional.empty(), Optional.empty(), Optional.of(byWallet));

    assertThat(resolution.type())
        .isEqualTo(WalletRegistrationDuplicateResolutionType.WALLET_HAS_PENDING_SESSION);
    assertThat(resolution.isConflict()).isTrue();
  }

  @Test
  void noDuplicates_allowsNewSessionCreation() {
    WalletRegistrationDuplicateResolution resolution =
        policy.resolve(1L, WALLET_ADDRESS, Optional.empty(), Optional.empty(), Optional.empty());

    assertThat(resolution.type()).isEqualTo(WalletRegistrationDuplicateResolutionType.CREATE_NEW);
    assertThat(resolution.isConflict()).isFalse();
  }

  private static WalletRegistrationSession session(Long userId, String walletAddress) {
    return WalletRegistrationSession.create(
        "session-" + userId + "-" + walletAddress.substring(2, 6),
        userId,
        walletAddress,
        "challenge-" + userId,
        NOW.plusMinutes(30),
        NOW);
  }
}
