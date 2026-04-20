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
  private final String internalIssuerSignerWalletAlias;

  public TreasuryAliasPolicyAdapter(
      RewardTokenProperties rewardTokenProperties,
      @Value("${web3.eip7702.sponsor.wallet-alias:}") String sponsorWalletAlias,
      @Value("${web3.execution.internal-issuer.signer.wallet-alias:}")
          String internalIssuerSignerWalletAlias) {
    this.rewardTokenProperties = rewardTokenProperties;
    this.sponsorWalletAlias = sponsorWalletAlias;
    this.internalIssuerSignerWalletAlias = internalIssuerSignerWalletAlias;
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
    if (internalIssuerSignerWalletAlias != null && !internalIssuerSignerWalletAlias.isBlank()) {
      aliases.add(internalIssuerSignerWalletAlias.trim());
    }
    return Set.copyOf(aliases);
  }
}
