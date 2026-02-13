package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter.web3;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.config.RewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.GasFeeStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class DefaultGasFeeStrategy implements GasFeeStrategy {

  private final RewardTokenProperties rewardTokenProperties;

  @Override
  public FeePlan calculate(FeeInputs inputs) {
    BigInteger defaultGasLimit =
        BigInteger.valueOf(rewardTokenProperties.getGas().getDefaultGasLimit());
    BigInteger defaultPriorityFee =
        BigInteger.valueOf(rewardTokenProperties.getGas().getDefaultMaxPriorityFeePerGasWei());
    BigInteger multiplier =
        BigInteger.valueOf(rewardTokenProperties.getGas().getMaxFeeMultiplier());

    BigInteger gasLimit = positiveOrDefault(inputs.estimatedGas(), defaultGasLimit);
    BigInteger priorityFee = positiveOrDefault(inputs.maxPriorityFeePerGas(), defaultPriorityFee);

    BigInteger maxFee;
    if (isPositive(inputs.baseFeePerGas())) {
      maxFee = inputs.baseFeePerGas().multiply(multiplier).add(priorityFee);
    } else if (isPositive(inputs.gasPrice())) {
      maxFee = inputs.gasPrice().add(priorityFee);
    } else {
      maxFee = priorityFee.multiply(multiplier);
    }

    if (maxFee.compareTo(priorityFee) < 0) {
      maxFee = priorityFee;
    }
    return new FeePlan(gasLimit, priorityFee, maxFee);
  }

  private BigInteger positiveOrDefault(BigInteger value, BigInteger defaultValue) {
    if (!isPositive(value)) {
      return defaultValue;
    }
    return value;
  }

  private boolean isPositive(BigInteger value) {
    return value != null && value.signum() > 0;
  }
}
