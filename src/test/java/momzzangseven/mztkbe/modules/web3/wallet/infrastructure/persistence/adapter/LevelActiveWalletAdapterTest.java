package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LevelActiveWalletAdapterTest {

  @Mock private LoadWalletPort loadWalletPort;

  private LevelActiveWalletAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new LevelActiveWalletAdapter(loadWalletPort);
  }

  @Test
  void loadActiveWalletAddress_returnsNormalizedAddress_whenWalletExists() {
    when(loadWalletPort.findWalletsByUserIdAndStatus(7L, WalletStatus.ACTIVE))
        .thenReturn(
            List.of(
                UserWallet.builder()
                    .id(1L)
                    .userId(7L)
                    .walletAddress("0x5Aaeb6053f3E94C9b9A09f33669435E7Ef1BeAed")
                    .status(WalletStatus.ACTIVE)
                    .registeredAt(Instant.now())
                    .build()));

    Optional<EvmAddress> address = adapter.loadActiveWalletAddress(7L);

    assertThat(address).isPresent();
    assertThat(address.get().value()).isEqualTo("0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed");
  }

  @Test
  void loadActiveWalletAddress_returnsEmpty_whenNoWallet() {
    when(loadWalletPort.findWalletsByUserIdAndStatus(7L, WalletStatus.ACTIVE))
        .thenReturn(List.of());

    assertThat(adapter.loadActiveWalletAddress(7L)).isEmpty();
  }
}
