package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorPolicyResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetSponsorPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalSponsorPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletApprovalSponsorPolicyAdapterTest {

  @Mock private GetSponsorPolicyUseCase getSponsorPolicyUseCase;

  @Test
  void load_mapsExecutionPolicyInputPortResultToWalletOwnedDto() {
    when(getSponsorPolicyUseCase.execute())
        .thenReturn(
            new SponsorPolicyResult(
                true, 1_000_000L, 120L, 3L, new BigDecimal("0.002"), new BigDecimal("0.01")));
    WalletApprovalSponsorPolicyAdapter adapter =
        new WalletApprovalSponsorPolicyAdapter(getSponsorPolicyUseCase);

    WalletApprovalSponsorPolicy result = adapter.load();

    assertThat(result.enabled()).isTrue();
    assertThat(result.maxGasLimit()).isEqualTo(1_000_000L);
    assertThat(result.maxMaxFeeGwei()).isEqualTo(120L);
    assertThat(result.perTxCapEth()).isEqualByComparingTo("0.002");
    assertThat(result.perDayUserCapEth()).isEqualByComparingTo("0.01");
  }
}
