package momzzangseven.mztkbe.modules.web3.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SponsorTreasurySignerPropertiesTest {

  @Test
  void load_prefersInternalExecutionSigner_whenBothPrefixesMatch() {
    SponsorTreasurySignerProperties properties =
        new SponsorTreasurySignerProperties(
            new MockEnvironment()
                .withProperty("web3.eip7702.enabled", "true")
                .withProperty("web3.eip7702.sponsor.enabled", "true")
                .withProperty("web3.execution.internal.enabled", "true")
                .withProperty("web3.eip7702.sponsor.wallet-alias", " sponsor-main ")
                .withProperty("web3.execution.internal.signer.wallet-alias", "sponsor-main"));

    assertThat(properties.getWalletAlias()).isEqualTo("sponsor-main");
  }

  @Test
  void load_usesEip7702Value_whenInternalExecutionSignerIsAbsent() {
    SponsorTreasurySignerProperties properties =
        new SponsorTreasurySignerProperties(
            new MockEnvironment()
                .withProperty("web3.eip7702.enabled", "true")
                .withProperty("web3.eip7702.sponsor.enabled", "true")
                .withProperty("web3.eip7702.sponsor.wallet-alias", "sponsor-main"));

    assertThat(properties.getWalletAlias()).isEqualTo("sponsor-main");
  }

  @Test
  void constructor_rejectsMismatchedAliases() {
    assertThatThrownBy(
            () ->
                new SponsorTreasurySignerProperties(
                    new MockEnvironment()
                        .withProperty("web3.eip7702.enabled", "true")
                        .withProperty("web3.eip7702.sponsor.enabled", "true")
                        .withProperty("web3.execution.internal.enabled", "true")
                        .withProperty("web3.eip7702.sponsor.wallet-alias", "sponsor-main")
                        .withProperty(
                            "web3.execution.internal.signer.wallet-alias", "internal-main")))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("wallet-alias");
  }

  @Test
  void load_ignoresEip7702SponsorValues_whenSponsorFeatureDisabled() {
    SponsorTreasurySignerProperties properties =
        new SponsorTreasurySignerProperties(
            new MockEnvironment()
                .withProperty("web3.eip7702.enabled", "true")
                .withProperty("web3.eip7702.sponsor.enabled", "false")
                .withProperty("web3.eip7702.sponsor.wallet-alias", "sponsor-main")
                .withProperty("web3.execution.internal.enabled", "true")
                .withProperty("web3.execution.internal.signer.wallet-alias", "internal-main"));

    assertThat(properties.getWalletAlias()).isEqualTo("internal-main");
  }
}
