package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationDuplicateResolution;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationDuplicateResolutionType;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class WalletRegistrationSessionDuplicateResolverTest {

  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-13T10:00:00");
  private static final String WALLET_ADDRESS = "0x" + "a".repeat(40);

  @Mock private LoadWalletRegistrationSessionPort loadSessionPort;

  private WalletRegistrationSessionDuplicateResolver resolver;

  @BeforeEach
  void setUp() {
    resolver =
        new WalletRegistrationSessionDuplicateResolver(
            loadSessionPort, new WalletRegistrationSessionDuplicatePolicy());
  }

  @Test
  void resolveCurrent_reusesExactUserAndWalletSession() {
    WalletRegistrationSession existing = session(1L, WALLET_ADDRESS);
    when(loadSessionPort.loadLatestNonTerminalByUserIdAndWalletAddress(1L, WALLET_ADDRESS))
        .thenReturn(Optional.of(existing));
    when(loadSessionPort.loadLatestNonTerminalByUserId(1L)).thenReturn(Optional.empty());
    when(loadSessionPort.loadLatestNonTerminalByWalletAddress(WALLET_ADDRESS))
        .thenReturn(Optional.empty());

    WalletRegistrationDuplicateResolution resolution = resolver.resolveCurrent(1L, WALLET_ADDRESS);

    assertThat(resolution.type())
        .isEqualTo(WalletRegistrationDuplicateResolutionType.REUSE_EXISTING);
    assertThat(resolution.session()).isSameAs(existing);
  }

  @Test
  void resolveAfterCreateRace_usesRequiresNewReadBoundary() throws Exception {
    Transactional transactional =
        WalletRegistrationSessionDuplicateResolver.class
            .getMethod("resolveAfterCreateRace", Long.class, String.class)
            .getAnnotation(Transactional.class);

    assertThat(transactional).isNotNull();
    assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    assertThat(transactional.readOnly()).isTrue();
  }

  @Test
  void resolveAfterCreateRace_mapsWinningUserRowToConflict() {
    WalletRegistrationSession byUser = session(1L, "0x" + "b".repeat(40));
    when(loadSessionPort.loadLatestNonTerminalByUserIdAndWalletAddress(1L, WALLET_ADDRESS))
        .thenReturn(Optional.empty());
    when(loadSessionPort.loadLatestNonTerminalByUserId(1L)).thenReturn(Optional.of(byUser));
    when(loadSessionPort.loadLatestNonTerminalByWalletAddress(WALLET_ADDRESS))
        .thenReturn(Optional.empty());

    WalletRegistrationDuplicateResolution resolution =
        resolver.resolveAfterCreateRace(1L, WALLET_ADDRESS);

    assertThat(resolution.type())
        .isEqualTo(WalletRegistrationDuplicateResolutionType.USER_HAS_PENDING_SESSION);
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
