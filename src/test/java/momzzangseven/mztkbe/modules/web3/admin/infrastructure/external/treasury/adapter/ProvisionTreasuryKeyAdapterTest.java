package momzzangseven.mztkbe.modules.web3.admin.infrastructure.external.treasury.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ProvisionTreasuryKeyUseCase;
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
  void provision_mapsTokenResultToAdminResult() {
    when(provisionTreasuryKeyUseCase.execute(1L, "reward-main", "f".repeat(64)))
        .thenReturn(
            momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyResult
                .of("0x" + "a".repeat(40), "enc", "kek-base64"));

    ProvisionTreasuryKeyResult result = adapter.provision(1L, "reward-main", "f".repeat(64));

    assertThat(result.treasuryKeyEncryptionKeyB64()).isEqualTo("kek-base64");
    verify(provisionTreasuryKeyUseCase).execute(1L, "reward-main", "f".repeat(64));
  }
}
