package momzzangseven.mztkbe.modules.web3.token.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadRewardTreasuryAliasPort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadSponsorTreasuryAliasPort;
import org.junit.jupiter.api.Test;

class TreasuryAliasPolicyAdapterTest {

  @Test
  void defaultRewardTreasuryAlias_returnsConfiguredAlias() {
    TreasuryAliasPolicyAdapter adapter =
        new TreasuryAliasPolicyAdapter(
            rewardAliasPort("reward-main"), sponsorAliasPort("sponsor-main"));

    assertThat(adapter.defaultRewardTreasuryAlias()).isEqualTo("reward-main");
  }

  @Test
  void allowedAliases_includesTrimmedRewardAndSponsorAliases() {
    TreasuryAliasPolicyAdapter adapter =
        new TreasuryAliasPolicyAdapter(
            rewardAliasPort(" reward-main "), sponsorAliasPort(" sponsor-main "));

    assertThat(adapter.allowedAliases()).isEqualTo(Set.of("reward-main", "sponsor-main"));
  }

  @Test
  void allowedAliases_excludesBlankAliases() {
    TreasuryAliasPolicyAdapter adapter =
        new TreasuryAliasPolicyAdapter(rewardAliasPort(" "), sponsorAliasPort(" "));

    assertThat(adapter.allowedAliases()).isEmpty();
  }

  @Test
  void allowedAliases_excludesNullAliases() {
    TreasuryAliasPolicyAdapter adapter =
        new TreasuryAliasPolicyAdapter(rewardAliasPort(null), sponsorAliasPort(null));

    assertThat(adapter.allowedAliases()).isEmpty();
  }

  @Test
  void allowedAliases_deduplicatesSameAlias() {
    TreasuryAliasPolicyAdapter adapter =
        new TreasuryAliasPolicyAdapter(rewardAliasPort("shared"), sponsorAliasPort("shared"));

    assertThat(adapter.allowedAliases()).isEqualTo(Set.of("shared"));
  }

  private LoadRewardTreasuryAliasPort rewardAliasPort(String alias) {
    return () -> Optional.ofNullable(alias);
  }

  private LoadSponsorTreasuryAliasPort sponsorAliasPort(String alias) {
    return () -> Optional.ofNullable(alias);
  }
}
