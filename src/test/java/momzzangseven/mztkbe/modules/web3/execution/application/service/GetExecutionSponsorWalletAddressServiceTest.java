package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorKeyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorWalletConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionSponsorWalletConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetExecutionSponsorWalletAddressServiceTest {

  @Mock private LoadExecutionSponsorWalletConfigPort loadExecutionSponsorWalletConfigPort;
  @Mock private LoadExecutionSponsorKeyPort loadExecutionSponsorKeyPort;

  private GetExecutionSponsorWalletAddressService service;

  @BeforeEach
  void setUp() {
    service =
        new GetExecutionSponsorWalletAddressService(
            loadExecutionSponsorWalletConfigPort, loadExecutionSponsorKeyPort);
    when(loadExecutionSponsorWalletConfigPort.loadSponsorWalletConfig())
        .thenReturn(new ExecutionSponsorWalletConfig("alias", "kek"));
  }

  @Test
  void execute_returnsNormalizedAddress() {
    when(loadExecutionSponsorKeyPort.loadByAlias("alias", "kek"))
        .thenReturn(
            Optional.of(
                new LoadExecutionSponsorKeyPort.ExecutionSponsorKey(
                    "0xd799CD2B5258eDC2157beC7E2CD069f31f2678c2", "0x" + "9".repeat(64))));

    assertThat(service.execute()).isEqualTo("0xd799cd2b5258edc2157bec7e2cd069f31f2678c2");
  }

  @Test
  void execute_throwsWhenKeyMissing() {
    when(loadExecutionSponsorKeyPort.loadByAlias("alias", "kek")).thenReturn(Optional.empty());

    assertThatThrownBy(service::execute)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("sponsor signer key is missing");
  }
}
