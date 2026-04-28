package momzzangseven.mztkbe.modules.web3.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ProvisionTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionTreasuryKeyServiceTest {

  @Mock private ProvisionTreasuryKeyPort provisionTreasuryKeyPort;

  private ProvisionTreasuryKeyService service;

  @BeforeEach
  void setUp() {
    service = new ProvisionTreasuryKeyService(provisionTreasuryKeyPort);
  }

  @Test
  void execute_throws_whenCommandNull() {
    assertThatThrownBy(() -> service.execute(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  void execute_delegatesToPort_whenValid() {
    String address = "0x" + "a".repeat(40);
    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, "f".repeat(64), TreasuryRole.REWARD, address);
    ProvisionTreasuryKeyResult expected =
        new ProvisionTreasuryKeyResult(
            "reward-treasury",
            TreasuryRole.REWARD,
            "kms-key-id",
            address,
            TreasuryWalletStatus.ACTIVE,
            TreasuryKeyOrigin.IMPORTED,
            LocalDateTime.now());

    when(provisionTreasuryKeyPort.provision(1L, "f".repeat(64), TreasuryRole.REWARD, address))
        .thenReturn(expected);

    ProvisionTreasuryKeyResult result = service.execute(command);

    assertThat(result).isEqualTo(expected);
    verify(provisionTreasuryKeyPort).provision(1L, "f".repeat(64), TreasuryRole.REWARD, address);
  }
}
