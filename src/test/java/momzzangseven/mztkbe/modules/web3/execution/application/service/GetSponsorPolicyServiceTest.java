package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorPolicyResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SponsorPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetSponsorPolicyServiceTest {

  @Mock private LoadSponsorPolicyPort loadSponsorPolicyPort;

  private GetSponsorPolicyService service;

  @BeforeEach
  void setUp() {
    service = new GetSponsorPolicyService(loadSponsorPolicyPort);
  }

  @Test
  void execute_mapsLoadedSponsorPolicyToApplicationResult() {
    SponsorPolicy policy =
        new SponsorPolicy(
            true, 1_000_000L, 120L, 3L, new BigDecimal("0.002"), new BigDecimal("0.01"));
    when(loadSponsorPolicyPort.loadSponsorPolicy()).thenReturn(policy);

    SponsorPolicyResult result = service.execute();

    assertThat(result.enabled()).isTrue();
    assertThat(result.maxGasLimit()).isEqualTo(1_000_000L);
    assertThat(result.maxMaxFeeGwei()).isEqualTo(120L);
    assertThat(result.maxPriorityFeeGwei()).isEqualTo(3L);
    assertThat(result.perTxCapEth()).isEqualByComparingTo("0.002");
    assertThat(result.perDayUserCapEth()).isEqualByComparingTo("0.01");
  }
}
