package momzzangseven.mztkbe.modules.web3.shared.infrastructure.config;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySignerConfigView;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.LoadRewardTreasurySignerConfigSourcePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RewardTreasurySignerConfigPropertiesAdapter
    implements LoadRewardTreasurySignerConfigSourcePort {

  private final RewardTreasurySignerProperties rewardTreasurySignerProperties;

  @Override
  public TreasurySignerConfigView load() {
    return new TreasurySignerConfigView(rewardTreasurySignerProperties.getWalletAlias());
  }
}
