package momzzangseven.mztkbe.modules.web3.token.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TreasuryAliasPolicyAdapterTest {

  @Test
  void defaultRewardTreasuryAlias_returnsConfiguredAlias() {
    RewardTokenProperties properties = new RewardTokenProperties();
    properties.getTreasury().setWalletAlias("reward-main");

    TreasuryAliasPolicyAdapter adapter = new TreasuryAliasPolicyAdapter(properties, "sponsor-main");

    assertThat(adapter.defaultRewardTreasuryAlias()).isEqualTo("reward-main");
  }

  @Test
  void allowedAliases_includesTrimmedRewardAndSponsorAliases() {
    RewardTokenProperties properties = new RewardTokenProperties();
    properties.getTreasury().setWalletAlias(" reward-main ");

    TreasuryAliasPolicyAdapter adapter =
        new TreasuryAliasPolicyAdapter(properties, " sponsor-main ");

    assertThat(adapter.allowedAliases()).isEqualTo(Set.of("reward-main", "sponsor-main"));
  }

  @Test
  void allowedAliases_excludesBlankAliases() {
    RewardTokenProperties properties = new RewardTokenProperties();
    properties.getTreasury().setWalletAlias(" ");

    TreasuryAliasPolicyAdapter adapter = new TreasuryAliasPolicyAdapter(properties, " ");

    assertThat(adapter.allowedAliases()).isEmpty();
  }

  @Test
  void allowedAliases_excludesNullAliases() {
    RewardTokenProperties properties = new RewardTokenProperties();
    properties.getTreasury().setWalletAlias(null);

    TreasuryAliasPolicyAdapter adapter = new TreasuryAliasPolicyAdapter(properties, null);

    assertThat(adapter.allowedAliases()).isEmpty();
  }

  @Test
  void allowedAliases_deduplicatesSameAlias() {
    RewardTokenProperties properties = new RewardTokenProperties();
    properties.getTreasury().setWalletAlias("shared");

    TreasuryAliasPolicyAdapter adapter = new TreasuryAliasPolicyAdapter(properties, "shared");

    assertThat(adapter.allowedAliases()).isEqualTo(Set.of("shared"));
  }
}
