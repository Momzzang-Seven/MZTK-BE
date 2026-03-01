package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import org.junit.jupiter.api.Test;

class CreateLevelUpRewardTxIntentCommandTest {

  @Test
  void constructor_setsReferenceIdFromLevelUpHistoryId() {
    CreateLevelUpRewardTxIntentCommand command =
        new CreateLevelUpRewardTxIntentCommand(
            3L,
            77L,
            "idem-1",
            EvmAddress.of("0x5Aaeb6053f3E94C9b9A09f33669435E7Ef1BeAed"),
            EvmAddress.of("0x742d35Cc6634C0532925a3b844Bc454e4438f44e"),
            BigInteger.ONE);

    assertThat(command.referenceId()).isEqualTo("77");
    assertThat(command.amountWei()).isEqualTo(BigInteger.ONE);
  }

  @Test
  void constructor_throws_whenAmountNegative() {
    assertThatThrownBy(
            () ->
                new CreateLevelUpRewardTxIntentCommand(
                    3L,
                    77L,
                    "idem-1",
                    EvmAddress.of("0x5Aaeb6053f3E94C9b9A09f33669435E7Ef1BeAed"),
                    EvmAddress.of("0x742d35Cc6634C0532925a3b844Bc454e4438f44e"),
                    BigInteger.valueOf(-1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("amountWei");
  }
}
