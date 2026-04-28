package momzzangseven.mztkbe.modules.web3.admin.infrastructure.external.treasury.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ProvisionTreasuryKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionTreasuryKeyAdapterTest {

  @Mock private ProvisionTreasuryKeyUseCase provisionTreasuryKeyUseCase;

  private ProvisionTreasuryKeyAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new ProvisionTreasuryKeyAdapter(provisionTreasuryKeyUseCase);
  }

  @Test
  void provision_mapsTreasuryResultToAdminResult() {
    String address = "0x" + "a".repeat(40);
    var treasuryResult =
        new momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyResult(
            "reward-treasury",
            TreasuryRole.REWARD,
            "kms-key-id",
            address,
            TreasuryWalletStatus.ACTIVE,
            TreasuryKeyOrigin.IMPORTED,
            LocalDateTime.now());
    when(provisionTreasuryKeyUseCase.execute(
            new ProvisionTreasuryKeyCommand(1L, "f".repeat(64), TreasuryRole.REWARD, address)))
        .thenReturn(treasuryResult);

    ProvisionTreasuryKeyResult result =
        adapter.provision(1L, "f".repeat(64), TreasuryRole.REWARD, address);

    assertThat(result.kmsKeyId()).isEqualTo("kms-key-id");
    assertThat(result.walletAddress()).isEqualTo(address);
    assertThat(result.status()).isEqualTo(TreasuryWalletStatus.ACTIVE);
    verify(provisionTreasuryKeyUseCase)
        .execute(
            new ProvisionTreasuryKeyCommand(1L, "f".repeat(64), TreasuryRole.REWARD, address));
  }
}
