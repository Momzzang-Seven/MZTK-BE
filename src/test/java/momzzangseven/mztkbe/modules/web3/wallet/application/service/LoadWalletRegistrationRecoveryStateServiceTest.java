package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.LoadWalletRegistrationRecoveryStateQuery;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoadWalletRegistrationRecoveryStateServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 22, 12, 0);
  private static final String WALLET_ADDRESS = "0x" + "b".repeat(40);

  @Mock private LoadWalletRegistrationSessionPort loadSessionPort;

  private LoadWalletRegistrationRecoveryStateService service;

  @BeforeEach
  void setUp() {
    service = new LoadWalletRegistrationRecoveryStateService(loadSessionPort);
  }

  @Test
  void execute_includesNewerAuthoritativeSessionFlag() {
    WalletRegistrationSession session = session();
    when(loadSessionPort.loadByPublicId("registration-1")).thenReturn(Optional.of(session));
    when(loadSessionPort.existsNewerByUserIdOrWalletAddress(7L, WALLET_ADDRESS, 1L))
        .thenReturn(true);

    var result = service.execute(new LoadWalletRegistrationRecoveryStateQuery("registration-1"));

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().newerWalletRegistrationExists()).isTrue();
    verify(loadSessionPort).existsNewerByUserIdOrWalletAddress(7L, WALLET_ADDRESS, 1L);
  }

  private WalletRegistrationSession session() {
    return WalletRegistrationSession.builder()
        .id(1L)
        .publicId("registration-1")
        .userId(7L)
        .walletAddress(WALLET_ADDRESS)
        .challengeNonce("nonce-1")
        .status(WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN)
        .latestExecutionIntentId("intent-1")
        .latestTransactionId(24L)
        .latestTransactionHash("0x" + "a".repeat(64))
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();
  }
}
