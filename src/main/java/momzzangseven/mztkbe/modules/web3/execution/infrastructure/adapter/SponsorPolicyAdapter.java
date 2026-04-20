package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SponsorPolicy;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.config.ExecutionEip7702Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class SponsorPolicyAdapter implements LoadSponsorPolicyPort {

  private final ExecutionEip7702Properties executionEip7702Properties;

  @Override
  public SponsorPolicy loadSponsorPolicy() {
    ExecutionEip7702Properties.Sponsor sponsor = executionEip7702Properties.getSponsor();
    return new SponsorPolicy(
        sponsor.getEnabled(),
        sponsor.getMaxGasLimit(),
        sponsor.getMaxMaxFeeGwei(),
        sponsor.getMaxPriorityFeeGwei(),
        sponsor.getPerTxCapEth(),
        sponsor.getPerDayUserCapEth());
  }
}
