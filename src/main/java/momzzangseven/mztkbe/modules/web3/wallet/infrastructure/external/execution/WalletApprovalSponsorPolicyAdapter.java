package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.execution;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorPolicyResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetSponsorPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalSponsorPolicy;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalSponsorPolicyPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class WalletApprovalSponsorPolicyAdapter implements LoadWalletApprovalSponsorPolicyPort {

  private final GetSponsorPolicyUseCase getSponsorPolicyUseCase;

  @Override
  public WalletApprovalSponsorPolicy load() {
    SponsorPolicyResult policy = getSponsorPolicyUseCase.execute();
    return new WalletApprovalSponsorPolicy(
        policy.enabled(),
        policy.maxGasLimit(),
        policy.maxMaxFeeGwei(),
        policy.perTxCapEth(),
        policy.perDayUserCapEth());
  }
}
