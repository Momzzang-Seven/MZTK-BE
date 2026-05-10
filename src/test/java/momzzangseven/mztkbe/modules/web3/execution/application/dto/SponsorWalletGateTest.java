package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SponsorWalletGate compact constructor")
class SponsorWalletGateTest {

  private static final String ALIAS = "sponsor-treasury";
  private static final String KMS_KEY_ID = "alias/sponsor-treasury";
  private static final String ADDRESS = "0x" + "6".repeat(40);

  private static TreasuryWalletInfo info() {
    return new TreasuryWalletInfo(ALIAS, KMS_KEY_ID, ADDRESS, true);
  }

  private static TreasurySigner signer() {
    return new TreasurySigner(ALIAS, KMS_KEY_ID, ADDRESS);
  }

  @Test
  @DisplayName("[M-21] both walletInfo and signer present → constructor succeeds")
  void compactConstructor_succeeds_whenBothComponentsPresent() {
    SponsorWalletGate gate = new SponsorWalletGate(info(), signer());

    assertThat(gate.walletInfo().walletAlias()).isEqualTo(ALIAS);
    assertThat(gate.signer().walletAlias()).isEqualTo(ALIAS);
    assertThat(gate.signer().kmsKeyId()).isEqualTo(KMS_KEY_ID);
    assertThat(gate.signer().walletAddress()).isEqualTo(ADDRESS);
  }

  @Test
  @DisplayName("[M-22] walletInfo == null → IllegalArgumentException(\"walletInfo required\")")
  void compactConstructor_throws_whenWalletInfoNull() {
    assertThatThrownBy(() -> new SponsorWalletGate(null, signer()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("walletInfo required");
  }

  @Test
  @DisplayName("[M-23] signer == null → IllegalArgumentException(\"signer required\")")
  void compactConstructor_throws_whenSignerNull() {
    assertThatThrownBy(() -> new SponsorWalletGate(info(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("signer required");
  }
}
