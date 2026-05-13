package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.execution;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SponsorPolicy;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalSponsorPolicy;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalSponsorPolicyPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
@ConditionalOnBean(LoadSponsorPolicyPort.class)
public class WalletApprovalSponsorPolicyAdapter implements LoadWalletApprovalSponsorPolicyPort {

  private final LoadSponsorPolicyPort loadSponsorPolicyPort;

  @Override
  public WalletApprovalSponsorPolicy load() {
    SponsorPolicy policy = loadSponsorPolicyPort.loadSponsorPolicy();
    return new WalletApprovalSponsorPolicy(
        policy.enabled(),
        policy.maxGasLimit(),
        policy.maxMaxFeeGwei(),
        policy.perTxCapEth(),
        policy.perDayUserCapEth());
  }
}
