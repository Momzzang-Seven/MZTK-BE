package momzzangseven.mztkbe.modules.web3.token.infrastructure.config;

import java.util.LinkedHashSet;
import java.util.Set;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadRewardTreasuryAliasPort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadSponsorTreasuryAliasPort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryAliasPolicyPort;
import org.springframework.stereotype.Component;

@Component
public class TreasuryAliasPolicyAdapter implements LoadTreasuryAliasPolicyPort {

  private final LoadRewardTreasuryAliasPort loadRewardTreasuryAliasPort;
  private final LoadSponsorTreasuryAliasPort loadSponsorTreasuryAliasPort;

  public TreasuryAliasPolicyAdapter(
      LoadRewardTreasuryAliasPort loadRewardTreasuryAliasPort,
      LoadSponsorTreasuryAliasPort loadSponsorTreasuryAliasPort) {
    this.loadRewardTreasuryAliasPort = loadRewardTreasuryAliasPort;
    this.loadSponsorTreasuryAliasPort = loadSponsorTreasuryAliasPort;
  }

  @Override
  public String defaultRewardTreasuryAlias() {
    return loadRewardTreasuryAliasPort.loadAlias().orElse(null);
  }

  @Override
  public Set<String> allowedAliases() {
    Set<String> aliases = new LinkedHashSet<>();
    String rewardTreasuryAlias = loadRewardTreasuryAliasPort.loadAlias().orElse(null);
    if (rewardTreasuryAlias != null && !rewardTreasuryAlias.isBlank()) {
      aliases.add(rewardTreasuryAlias.trim());
    }
    String sponsorWalletAlias = loadSponsorTreasuryAliasPort.loadAlias().orElse(null);
    if (sponsorWalletAlias != null && !sponsorWalletAlias.isBlank()) {
      aliases.add(sponsorWalletAlias.trim());
    }
    return Set.copyOf(aliases);
  }
}
