package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MarketplaceGasFeeCalculatorTest {

  private MarketplaceGasFeeCalculator calculator;

  @BeforeEach
  void setUp() {
    calculator = new MarketplaceGasFeeCalculator();
    ReflectionTestUtils.setField(calculator, "defaultGasLimit", 21_000L);
    ReflectionTestUtils.setField(calculator, "defaultMaxPriorityFeePerGasWei", 2L);
    ReflectionTestUtils.setField(calculator, "maxFeeMultiplier", 2);
    ReflectionTestUtils.setField(calculator, "estimatedGasBufferPercent", 130);
  }

  @Test
  @DisplayName("baseFee가 있으면 baseFee * multiplier + priorityFee로 maxFee를 계산한다")
  void calculateUsesBaseFeeBranch() {
    MarketplaceGasFeeCalculator.FeePlan plan =
        calculator.calculate(
            new MarketplaceGasFeeCalculator.FeeInputs(
                BigInteger.valueOf(100_000), null, BigInteger.valueOf(50), BigInteger.valueOf(40)));

    assertThat(plan.gasLimit()).isEqualTo(BigInteger.valueOf(130_000));
    assertThat(plan.maxPriorityFeePerGas()).isEqualTo(BigInteger.valueOf(2));
    assertThat(plan.maxFeePerGas()).isEqualTo(BigInteger.valueOf(102));
  }

  @Test
  @DisplayName("baseFee가 없고 gasPrice가 있으면 gasPrice + priorityFee로 maxFee를 계산한다")
  void calculateUsesGasPriceBranch() {
    MarketplaceGasFeeCalculator.FeePlan plan =
        calculator.calculate(
            new MarketplaceGasFeeCalculator.FeeInputs(
                BigInteger.valueOf(10_000), BigInteger.valueOf(3), null, BigInteger.valueOf(50)));

    assertThat(plan.gasLimit()).isEqualTo(BigInteger.valueOf(21_000));
    assertThat(plan.maxPriorityFeePerGas()).isEqualTo(BigInteger.valueOf(3));
    assertThat(plan.maxFeePerGas()).isEqualTo(BigInteger.valueOf(53));
  }

  @Test
  @DisplayName("fee 입력이 없으면 priorityFee * multiplier로 maxFee를 계산한다")
  void calculateUsesPriorityMultiplierFallback() {
    MarketplaceGasFeeCalculator.FeePlan plan =
        calculator.calculate(
            new MarketplaceGasFeeCalculator.FeeInputs(null, BigInteger.valueOf(5), null, null));

    assertThat(plan.gasLimit()).isEqualTo(BigInteger.valueOf(21_000));
    assertThat(plan.maxPriorityFeePerGas()).isEqualTo(BigInteger.valueOf(5));
    assertThat(plan.maxFeePerGas()).isEqualTo(BigInteger.valueOf(10));
  }

  @Test
  @DisplayName("maxFee가 priorityFee보다 작으면 priorityFee로 clamp한다")
  void calculateClampsMaxFeeToPriorityFee() {
    ReflectionTestUtils.setField(calculator, "maxFeeMultiplier", -1);

    MarketplaceGasFeeCalculator.FeePlan plan =
        calculator.calculate(
            new MarketplaceGasFeeCalculator.FeeInputs(
                BigInteger.valueOf(30_000), BigInteger.valueOf(10), BigInteger.valueOf(5), null));

    assertThat(plan.maxPriorityFeePerGas()).isEqualTo(BigInteger.valueOf(10));
    assertThat(plan.maxFeePerGas()).isEqualTo(BigInteger.valueOf(10));
  }

  @Test
  @DisplayName("FeeInputs는 음수와 0 입력을 차단한다")
  void feeInputsRejectInvalidValues() {
    assertThatThrownBy(
            () -> new MarketplaceGasFeeCalculator.FeeInputs(BigInteger.ZERO, null, null, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("estimatedGas must be > 0");
    assertThatThrownBy(
            () ->
                new MarketplaceGasFeeCalculator.FeeInputs(null, null, BigInteger.valueOf(-1), null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("baseFeePerGas must be >= 0");
    assertThatThrownBy(
            () -> new MarketplaceGasFeeCalculator.FeeInputs(null, null, null, BigInteger.ZERO))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("gasPrice must be > 0");
  }

  @Test
  @DisplayName("FeePlan은 null 또는 양수가 아닌 출력값을 차단한다")
  void feePlanRejectsInvalidValues() {
    assertThatThrownBy(
            () -> new MarketplaceGasFeeCalculator.FeePlan(null, BigInteger.ONE, BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("gasLimit must be > 0");
    assertThatThrownBy(
            () ->
                new MarketplaceGasFeeCalculator.FeePlan(
                    BigInteger.ONE, BigInteger.ZERO, BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxPriorityFeePerGas must be > 0");
    assertThatThrownBy(
            () ->
                new MarketplaceGasFeeCalculator.FeePlan(
                    BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("maxFeePerGas must be > 0");
  }
}
