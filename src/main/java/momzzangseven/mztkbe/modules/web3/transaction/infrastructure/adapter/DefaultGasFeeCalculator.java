package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnRewardTokenOrAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnRewardTokenOrAnyExecutionEnabled
public class DefaultGasFeeCalculator {

  private final TransactionRewardTokenProperties rewardTokenProperties;

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

  public record FeeInputs(
      BigInteger estimatedGas,
      BigInteger maxPriorityFeePerGas,
      BigInteger baseFeePerGas,
      BigInteger gasPrice) {

    public FeeInputs {
      if (estimatedGas != null && estimatedGas.signum() <= 0) {
        throw new Web3InvalidInputException("estimatedGas must be > 0");
      }
      if (baseFeePerGas != null && baseFeePerGas.signum() < 0) {
        throw new Web3InvalidInputException("baseFeePerGas must be >= 0");
      }
      if (gasPrice != null && gasPrice.signum() <= 0) {
        throw new Web3InvalidInputException("gasPrice must be > 0");
      }
    }
  }

  public record FeePlan(
      BigInteger gasLimit, BigInteger maxPriorityFeePerGas, BigInteger maxFeePerGas) {

    public FeePlan {
      if (gasLimit == null || gasLimit.signum() <= 0) {
        throw new Web3InvalidInputException("gasLimit must be > 0");
      }
      if (maxPriorityFeePerGas == null || maxPriorityFeePerGas.signum() <= 0) {
        throw new Web3InvalidInputException("maxPriorityFeePerGas must be > 0");
      }
      if (maxFeePerGas == null || maxFeePerGas.signum() <= 0) {
        throw new Web3InvalidInputException("maxFeePerGas must be > 0");
      }
    }
  }
}
