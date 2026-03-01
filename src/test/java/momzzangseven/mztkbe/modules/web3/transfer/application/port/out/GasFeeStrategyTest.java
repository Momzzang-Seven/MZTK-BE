package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class GasFeeStrategyTest {

  @Test
  void feeInputs_rejectsNonPositiveEstimatedGas() {
    assertThatThrownBy(() -> new GasFeeStrategy.FeeInputs(BigInteger.ZERO, null, null, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("estimatedGas must be > 0");
  }

  @Test
  void feeInputs_rejectsNegativeBaseFee() {
    assertThatThrownBy(
            () ->
                new GasFeeStrategy.FeeInputs(
                    BigInteger.TEN, BigInteger.ONE, BigInteger.valueOf(-1), null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("baseFeePerGas must be >= 0");
  }

  @Test
  void feeInputs_rejectsNonPositiveGasPrice() {
    assertThatThrownBy(
            () ->
                new GasFeeStrategy.FeeInputs(
                    BigInteger.TEN, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("gasPrice must be > 0");
  }

  @Test
  void feeInputs_allowsNullAndPositiveValues() {
    GasFeeStrategy.FeeInputs inputs =
        new GasFeeStrategy.FeeInputs(BigInteger.TEN, BigInteger.ONE, BigInteger.ZERO, null);

    assertThat(inputs.estimatedGas()).isEqualTo(BigInteger.TEN);
    assertThat(inputs.maxPriorityFeePerGas()).isEqualTo(BigInteger.ONE);
    assertThat(inputs.baseFeePerGas()).isEqualTo(BigInteger.ZERO);
  }

  @Test
  void feePlan_rejectsInvalidValues() {
    assertThatThrownBy(
            () -> new GasFeeStrategy.FeePlan(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("gasLimit must be > 0");

    assertThatThrownBy(
            () -> new GasFeeStrategy.FeePlan(BigInteger.ONE, BigInteger.ZERO, BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxPriorityFeePerGas must be > 0");

    assertThatThrownBy(
            () -> new GasFeeStrategy.FeePlan(BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxFeePerGas must be > 0");
  }

  @Test
  void feePlan_rejectsNullValues() {
    assertThatThrownBy(() -> new GasFeeStrategy.FeePlan(null, BigInteger.ONE, BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("gasLimit must be > 0");

    assertThatThrownBy(() -> new GasFeeStrategy.FeePlan(BigInteger.ONE, null, BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxPriorityFeePerGas must be > 0");

    assertThatThrownBy(() -> new GasFeeStrategy.FeePlan(BigInteger.ONE, BigInteger.ONE, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxFeePerGas must be > 0");
  }

  @Test
  void feePlan_acceptsPositiveValues() {
    GasFeeStrategy.FeePlan plan =
        new GasFeeStrategy.FeePlan(BigInteger.valueOf(100_000), BigInteger.ONE, BigInteger.TWO);

    assertThat(plan.gasLimit()).isEqualTo(BigInteger.valueOf(100_000));
  }
}
