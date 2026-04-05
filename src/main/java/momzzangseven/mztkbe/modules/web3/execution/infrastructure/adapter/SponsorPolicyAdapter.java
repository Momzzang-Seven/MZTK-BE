package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SponsorPolicy;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SponsorPolicyAdapter implements LoadSponsorPolicyPort {

  private final Eip7702Properties eip7702Properties;

  @Override
  public SponsorPolicy loadSponsorPolicy() {
    Eip7702Properties.Sponsor sponsor = eip7702Properties.getSponsor();
    return new SponsorPolicy(
        sponsor.isEnabled(),
        sponsor.getMaxGasLimit(),
        sponsor.getMaxMaxFeeGwei(),
        sponsor.getMaxPriorityFeeGwei(),
        sponsor.getPerTxCapEth(),
        sponsor.getPerDayUserCapEth());
  }
}
