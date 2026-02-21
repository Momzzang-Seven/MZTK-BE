package momzzangseven.mztkbe.modules.web3.token.infrastructure.config;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryAliasPolicyPort;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.Eip7702Properties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TreasuryAliasPolicyAdapter implements LoadTreasuryAliasPolicyPort {

  private final RewardTokenProperties rewardTokenProperties;
  private final Eip7702Properties eip7702Properties;

  @Override
  public String defaultRewardTreasuryAlias() {
    return rewardTokenProperties.getTreasury().getWalletAlias();
  }

  @Override
  public Set<String> allowedAliases() {
    return Set.of(
        rewardTokenProperties.getTreasury().getWalletAlias(),
        eip7702Properties.getSponsor().getWalletAlias());
  }
}
