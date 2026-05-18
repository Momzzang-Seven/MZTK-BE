package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.Eip7702AuthorizationPolicyResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetEip7702AuthorizationPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalTtlPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletApprovalTtlPolicyAdapterTest {

  @Mock private GetEip7702AuthorizationPolicyUseCase getEip7702AuthorizationPolicyUseCase;

  @Test
  void load_mapsExecutionPolicyInputPortResultToWalletOwnedDto() {
    when(getEip7702AuthorizationPolicyUseCase.execute())
        .thenReturn(new Eip7702AuthorizationPolicyResult(45L));
    WalletApprovalTtlPolicyAdapter adapter =
        new WalletApprovalTtlPolicyAdapter(getEip7702AuthorizationPolicyUseCase);

    WalletApprovalTtlPolicy result = adapter.load();

    assertThat(result.minimumRemainingSeconds()).isEqualTo(45L);
  }
}
