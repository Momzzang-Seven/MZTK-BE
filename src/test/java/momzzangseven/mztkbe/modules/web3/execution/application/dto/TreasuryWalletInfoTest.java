package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("TreasuryWalletInfo compact constructor")
class TreasuryWalletInfoTest {

  @Test
  @DisplayName(
      "[M-24] walletAlias 정상 → kmsKeyId/walletAddress 가 null 이고 active=false 여도 생성자 통과 (legacy backfill 대기)")
  void compactConstructor_succeeds_whenAliasOnly() {
    TreasuryWalletInfo info = new TreasuryWalletInfo("sponsor-treasury", null, null, false);

    assertThat(info.walletAlias()).isEqualTo("sponsor-treasury");
    assertThat(info.kmsKeyId()).isNull();
    assertThat(info.walletAddress()).isNull();
    assertThat(info.active()).isFalse();
  }

  @Test
  @DisplayName("[M-25] walletAlias == null → Web3InvalidInputException(\"walletAlias required\")")
  void compactConstructor_throws_whenAliasNull() {
    assertThatThrownBy(
            () -> new TreasuryWalletInfo(null, "alias/test", "0x" + "1".repeat(40), true))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("walletAlias required");
  }

  @ParameterizedTest(name = "walletAlias=\"{0}\" → throws")
  @ValueSource(strings = {"", "   ", "\t", "\n"})
  @DisplayName("[M-26] walletAlias blank/공백 문자열 → Web3InvalidInputException")
  void compactConstructor_throws_whenAliasBlank(String alias) {
    assertThatThrownBy(
            () -> new TreasuryWalletInfo(alias, "alias/test", "0x" + "1".repeat(40), true))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("walletAlias required");
  }
}
