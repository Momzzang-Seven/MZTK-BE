package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultGasFeeCalculatorTest {

  private DefaultGasFeeCalculator strategy;

  @BeforeEach
  void setUp() {
    TransactionRewardTokenProperties properties = new TransactionRewardTokenProperties();
    properties.getGas().setDefaultGasLimit(120_000L);
    properties.getGas().setDefaultMaxPriorityFeePerGasWei(1_000_000_000L);
    properties.getGas().setMaxFeeMultiplier(2);
    strategy = new DefaultGasFeeCalculator(properties);
  }

  @Test
  void calculate_usesBaseFee_whenPositive() {
    DefaultGasFeeCalculator.FeeInputs inputs =
        new DefaultGasFeeCalculator.FeeInputs(
            BigInteger.valueOf(21000),
            BigInteger.valueOf(3),
            BigInteger.valueOf(100),
            BigInteger.valueOf(150));

    DefaultGasFeeCalculator.FeePlan plan = strategy.calculate(inputs);

    assertThat(plan.gasLimit()).isEqualTo(BigInteger.valueOf(21000));
    assertThat(plan.maxPriorityFeePerGas()).isEqualTo(BigInteger.valueOf(3));
    assertThat(plan.maxFeePerGas()).isEqualTo(BigInteger.valueOf(203)); // baseFee*2 + priority
  }

  @Test
  void calculate_fallbackToGasPrice_whenBaseFeeMissing() {
    DefaultGasFeeCalculator.FeeInputs inputs =
        new DefaultGasFeeCalculator.FeeInputs(
            BigInteger.valueOf(22000), BigInteger.valueOf(5), null, BigInteger.valueOf(200));

    DefaultGasFeeCalculator.FeePlan plan = strategy.calculate(inputs);

    assertThat(plan.maxFeePerGas()).isEqualTo(BigInteger.valueOf(205)); // gasPrice + priority
  }

  @Test
  void calculate_fallbackToPriorityMultiplier_whenBaseFeeAndGasPriceMissing() {
    DefaultGasFeeCalculator.FeeInputs inputs =
        new DefaultGasFeeCalculator.FeeInputs(
            BigInteger.valueOf(23000), BigInteger.valueOf(7), null, null);

    DefaultGasFeeCalculator.FeePlan plan = strategy.calculate(inputs);

    assertThat(plan.maxFeePerGas()).isEqualTo(BigInteger.valueOf(14));
  }

  @Test
  void calculate_usesDefaults_whenEstimatedGasOrPriorityMissing() {
    DefaultGasFeeCalculator.FeeInputs inputs =
        new DefaultGasFeeCalculator.FeeInputs(null, null, null, null);

    DefaultGasFeeCalculator.FeePlan plan = strategy.calculate(inputs);

    assertThat(plan.gasLimit()).isEqualTo(BigInteger.valueOf(120_000L));
    assertThat(plan.maxPriorityFeePerGas()).isEqualTo(BigInteger.valueOf(1_000_000_000L));
    assertThat(plan.maxFeePerGas()).isEqualTo(BigInteger.valueOf(2_000_000_000L));
  }

  @Test
  void calculate_enforcesMaxFeeAtLeastPriorityFee() {
    TransactionRewardTokenProperties properties = new TransactionRewardTokenProperties();
    properties.getGas().setDefaultGasLimit(120_000L);
    properties.getGas().setDefaultMaxPriorityFeePerGasWei(10L);
    properties.getGas().setMaxFeeMultiplier(-1);
    DefaultGasFeeCalculator strategyWithNegativeMultiplier =
        new DefaultGasFeeCalculator(properties);
    DefaultGasFeeCalculator.FeeInputs inputs =
        new DefaultGasFeeCalculator.FeeInputs(
            BigInteger.valueOf(21000), BigInteger.valueOf(10), null, null);

    DefaultGasFeeCalculator.FeePlan plan = strategyWithNegativeMultiplier.calculate(inputs);

    assertThat(plan.maxPriorityFeePerGas()).isEqualTo(BigInteger.TEN);
    assertThat(plan.maxFeePerGas()).isEqualTo(BigInteger.TEN);
  }

  @Test
  void calculate_treatsZeroBaseFeeAsNonPositive() {
    DefaultGasFeeCalculator.FeeInputs inputs =
        new DefaultGasFeeCalculator.FeeInputs(
            BigInteger.valueOf(21000), BigInteger.valueOf(9), BigInteger.ZERO, null);

    DefaultGasFeeCalculator.FeePlan plan = strategy.calculate(inputs);

    assertThat(plan.maxFeePerGas()).isEqualTo(BigInteger.valueOf(18));
  }
}
