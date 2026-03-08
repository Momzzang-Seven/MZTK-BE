package momzzangseven.mztkbe.modules.web3.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ProvisionTreasuryKeyPort;
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
    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, "f".repeat(64), "reward-main");
    ProvisionTreasuryKeyResult expected = new ProvisionTreasuryKeyResult("kek-base64");

    when(provisionTreasuryKeyPort.provision(1L, "reward-main", "f".repeat(64)))
        .thenReturn(expected);

    ProvisionTreasuryKeyResult result = service.execute(command);

    assertThat(result).isEqualTo(expected);
    verify(provisionTreasuryKeyPort).provision(1L, "reward-main", "f".repeat(64));
  }
}
