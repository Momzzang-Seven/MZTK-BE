package momzzangseven.mztkbe.modules.web3.token.infrastructure.config;

import java.util.LinkedHashSet;
import java.util.Set;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryAliasPolicyPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TreasuryAliasPolicyAdapter implements LoadTreasuryAliasPolicyPort {

  private final RewardTokenProperties rewardTokenProperties;
  private final String sponsorWalletAlias;

  public TreasuryAliasPolicyAdapter(
      RewardTokenProperties rewardTokenProperties,
      @Value("${web3.eip7702.sponsor.wallet-alias:}") String sponsorWalletAlias) {
    this.rewardTokenProperties = rewardTokenProperties;
    this.sponsorWalletAlias = sponsorWalletAlias;
  }

  @Override
  public String defaultRewardTreasuryAlias() {
    return rewardTokenProperties.getTreasury().getWalletAlias();
  }

  @Override
  public Set<String> allowedAliases() {
    Set<String> aliases = new LinkedHashSet<>();
    String rewardTreasuryAlias = rewardTokenProperties.getTreasury().getWalletAlias();
    if (rewardTreasuryAlias != null && !rewardTreasuryAlias.isBlank()) {
      aliases.add(rewardTreasuryAlias.trim());
    }
    if (sponsorWalletAlias != null && !sponsorWalletAlias.isBlank()) {
      aliases.add(sponsorWalletAlias.trim());
    }
    return Set.copyOf(aliases);
  }
}
