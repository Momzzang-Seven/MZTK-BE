package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.GasFeeStrategy;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.TransferRewardTokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultGasFeeStrategyTest {

  private DefaultGasFeeStrategy strategy;

  @BeforeEach
  void setUp() {
    TransferRewardTokenProperties properties = new TransferRewardTokenProperties();
    properties.getGas().setDefaultGasLimit(120_000L);
    properties.getGas().setDefaultMaxPriorityFeePerGasWei(1_000_000_000L);
    properties.getGas().setMaxFeeMultiplier(2);
    strategy = new DefaultGasFeeStrategy(properties);
  }

  @Test
  void calculate_usesBaseFee_whenPositive() {
    GasFeeStrategy.FeeInputs inputs =
        new GasFeeStrategy.FeeInputs(
            BigInteger.valueOf(21000),
            BigInteger.valueOf(3),
            BigInteger.valueOf(100),
            BigInteger.valueOf(150));

    GasFeeStrategy.FeePlan plan = strategy.calculate(inputs);

    assertThat(plan.gasLimit()).isEqualTo(BigInteger.valueOf(21000));
    assertThat(plan.maxPriorityFeePerGas()).isEqualTo(BigInteger.valueOf(3));
    assertThat(plan.maxFeePerGas()).isEqualTo(BigInteger.valueOf(203)); // baseFee*2 + priority
  }

  @Test
  void calculate_fallbackToGasPrice_whenBaseFeeMissing() {
    GasFeeStrategy.FeeInputs inputs =
        new GasFeeStrategy.FeeInputs(
            BigInteger.valueOf(22000), BigInteger.valueOf(5), null, BigInteger.valueOf(200));

    GasFeeStrategy.FeePlan plan = strategy.calculate(inputs);

    assertThat(plan.maxFeePerGas()).isEqualTo(BigInteger.valueOf(205)); // gasPrice + priority
  }

  @Test
  void calculate_fallbackToPriorityMultiplier_whenBaseFeeAndGasPriceMissing() {
    GasFeeStrategy.FeeInputs inputs =
        new GasFeeStrategy.FeeInputs(BigInteger.valueOf(23000), BigInteger.valueOf(7), null, null);

    GasFeeStrategy.FeePlan plan = strategy.calculate(inputs);

    assertThat(plan.maxFeePerGas()).isEqualTo(BigInteger.valueOf(14));
  }

  @Test
  void calculate_usesDefaults_whenEstimatedGasOrPriorityMissing() {
    GasFeeStrategy.FeeInputs inputs = new GasFeeStrategy.FeeInputs(null, null, null, null);

    GasFeeStrategy.FeePlan plan = strategy.calculate(inputs);

    assertThat(plan.gasLimit()).isEqualTo(BigInteger.valueOf(120_000L));
    assertThat(plan.maxPriorityFeePerGas()).isEqualTo(BigInteger.valueOf(1_000_000_000L));
    assertThat(plan.maxFeePerGas()).isEqualTo(BigInteger.valueOf(2_000_000_000L));
  }

  @Test
  void calculate_enforcesMaxFeeAtLeastPriorityFee() {
    TransferRewardTokenProperties properties = new TransferRewardTokenProperties();
    properties.getGas().setDefaultGasLimit(120_000L);
    properties.getGas().setDefaultMaxPriorityFeePerGasWei(10L);
    properties.getGas().setMaxFeeMultiplier(-1);
    DefaultGasFeeStrategy strategyWithNegativeMultiplier = new DefaultGasFeeStrategy(properties);
    GasFeeStrategy.FeeInputs inputs =
        new GasFeeStrategy.FeeInputs(BigInteger.valueOf(21000), BigInteger.valueOf(10), null, null);

    GasFeeStrategy.FeePlan plan = strategyWithNegativeMultiplier.calculate(inputs);

    assertThat(plan.maxPriorityFeePerGas()).isEqualTo(BigInteger.TEN);
    assertThat(plan.maxFeePerGas()).isEqualTo(BigInteger.TEN);
  }

  @Test
  void calculate_treatsZeroBaseFeeAsNonPositive() {
    GasFeeStrategy.FeeInputs inputs =
        new GasFeeStrategy.FeeInputs(BigInteger.valueOf(21000), BigInteger.valueOf(9), BigInteger.ZERO, null);

    GasFeeStrategy.FeePlan plan = strategy.calculate(inputs);

    assertThat(plan.maxFeePerGas()).isEqualTo(BigInteger.valueOf(18));
  }
}
