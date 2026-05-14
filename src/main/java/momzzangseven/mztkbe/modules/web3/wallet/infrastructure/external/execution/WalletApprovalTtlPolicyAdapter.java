package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.execution;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.Eip7702AuthorizationPolicyResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetEip7702AuthorizationPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalTtlPolicy;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalTtlPolicyPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class WalletApprovalTtlPolicyAdapter implements LoadWalletApprovalTtlPolicyPort {

  private final GetEip7702AuthorizationPolicyUseCase getEip7702AuthorizationPolicyUseCase;

  @Override
  public WalletApprovalTtlPolicy load() {
    Eip7702AuthorizationPolicyResult result = getEip7702AuthorizationPolicyUseCase.execute();
    return new WalletApprovalTtlPolicy(result.minimumRemainingSeconds());
  }
}
