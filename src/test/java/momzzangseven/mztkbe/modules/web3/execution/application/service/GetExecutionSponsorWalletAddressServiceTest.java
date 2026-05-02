package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorTreasuryWalletPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetExecutionSponsorWalletAddressServiceTest {

  @Mock private LoadSponsorTreasuryWalletPort loadSponsorTreasuryWalletPort;

  private GetExecutionSponsorWalletAddressService service;

  @BeforeEach
  void setUp() {
    service = new GetExecutionSponsorWalletAddressService(loadSponsorTreasuryWalletPort);
  }

  @Test
  void getSponsorAddress_returnsAddress_whenWalletPresent() {
    TreasuryWalletInfo walletInfo =
        new TreasuryWalletInfo(
            "sponsor-alias", "kms-key-id", "0xd799CD2B5258eDC2157beC7E2CD069f31f2678c2", true);
    when(loadSponsorTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo));

    assertThat(service.execute()).isEqualTo("0xd799cd2b5258edc2157bec7e2cd069f31f2678c2");
  }

  @Test
  void getSponsorAddress_returnsEmpty_whenWalletAbsent() {
    when(loadSponsorTreasuryWalletPort.load()).thenReturn(Optional.empty());

    assertThatThrownBy(service::execute)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("sponsor signer key is missing");
  }
}
