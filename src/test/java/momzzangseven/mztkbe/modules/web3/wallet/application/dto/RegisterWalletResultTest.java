package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.junit.jupiter.api.Test;

class RegisterWalletResultTest {

  @Test
  void from_mapsWalletFields() {
    Instant registeredAt = Instant.parse("2025-01-01T00:00:00Z");
    UserWallet wallet =
        UserWallet.builder()
            .id(99L)
            .userId(1L)
            .walletAddress("0x" + "a".repeat(40))
            .status(WalletStatus.ACTIVE)
            .registeredAt(registeredAt)
            .build();

    RegisterWalletResult result = RegisterWalletResult.from(wallet);

    assertThat(result.walletId()).isEqualTo(99L);
    assertThat(result.walletAddress()).isEqualTo("0x" + "a".repeat(40));
    assertThat(result.registeredAt()).isEqualTo(registeredAt);
  }
}
