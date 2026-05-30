package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.InternalExecutionIssuerPolicyView;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetInternalExecutionIssuerPolicyUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceWeb3InternalExecutionPolicyAdapterTest {

  @Mock private GetInternalExecutionIssuerPolicyUseCase getPolicyUseCase;

  @Test
  void mapsInternalIssuerPolicyToReservationLocalDto() {
    given(getPolicyUseCase.getPolicy())
        .willReturn(new InternalExecutionIssuerPolicyView(true, false, false, true, true));

    var result =
        new MarketplaceWeb3InternalExecutionPolicyAdapter(getPolicyUseCase)
            .loadInternalExecutionPolicy();

    assertThat(result.enabled()).isTrue();
    assertThat(result.marketplaceAdminSettleEnabled()).isTrue();
    assertThat(result.marketplaceAdminRefundEnabled()).isTrue();
  }
}
