package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
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
    assertThat(result.status()).isEqualTo(WalletRegistrationStatus.REGISTERED);
    assertThat(result.walletAddress()).isEqualTo("0x" + "a".repeat(40));
    assertThat(result.registeredAt()).isEqualTo(registeredAt);
    assertThat(result.nextAction()).isEqualTo(WalletRegistrationNextAction.DONE);
  }

  @Test
  void pending_mapsSessionFields() {
    LocalDateTime now = LocalDateTime.parse("2026-05-13T10:00:00");
    WalletRegistrationSession session =
        WalletRegistrationSession.create(
                "registration-1", 1L, "0x" + "b".repeat(40), "nonce-1", now.plusMinutes(30), now)
            .attachApprovalIntent("intent-1", now.plusMinutes(30), now.plusSeconds(1));

    RegisterWalletResult result = RegisterWalletResult.pending(session, null);

    assertThat(result.registrationId()).isEqualTo("registration-1");
    assertThat(result.status()).isEqualTo(WalletRegistrationStatus.APPROVAL_REQUIRED);
    assertThat(result.walletId()).isNull();
    assertThat(result.registeredAt()).isNull();
    assertThat(result.walletAddress()).isEqualTo("0x" + "b".repeat(40));
    assertThat(result.nextAction()).isEqualTo(WalletRegistrationNextAction.RETRY_APPROVAL);
  }
}
