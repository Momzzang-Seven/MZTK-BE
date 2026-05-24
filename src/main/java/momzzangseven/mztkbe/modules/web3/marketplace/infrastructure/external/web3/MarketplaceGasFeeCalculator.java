package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAnyExecutionEnabled
class MarketplaceGasFeeCalculator {

  @Value("${web3.reward-token.gas.default-gas-limit}")
  private long defaultGasLimit;

  @Value("${web3.reward-token.gas.default-max-priority-fee-per-gas-wei}")
  private long defaultMaxPriorityFeePerGasWei;

  @Value("${web3.reward-token.gas.max-fee-multiplier}")
  private int maxFeeMultiplier;

  @Value("${web3.reward-token.gas.estimated-gas-buffer-percent:130}")
  private int estimatedGasBufferPercent;

  FeePlan calculate(FeeInputs inputs) {
    BigInteger gasLimit =
        applyGasBuffer(
            inputs.estimatedGas(),
            BigInteger.valueOf(defaultGasLimit),
            BigInteger.valueOf(estimatedGasBufferPercent));
    BigInteger priorityFee =
        positiveOrDefault(
            inputs.maxPriorityFeePerGas(), BigInteger.valueOf(defaultMaxPriorityFeePerGasWei));
    BigInteger multiplier = BigInteger.valueOf(maxFeeMultiplier);

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
    return isPositive(value) ? value : defaultValue;
  }

  private BigInteger applyGasBuffer(
      BigInteger estimatedGas, BigInteger fallbackGasLimit, BigInteger bufferPercent) {
    if (!isPositive(estimatedGas)) {
      return fallbackGasLimit;
    }
    BigInteger buffered = estimatedGas.multiply(bufferPercent).divide(BigInteger.valueOf(100L));
    return buffered.max(fallbackGasLimit);
  }

  private boolean isPositive(BigInteger value) {
    return value != null && value.signum() > 0;
  }

  record FeeInputs(
      BigInteger estimatedGas,
      BigInteger maxPriorityFeePerGas,
      BigInteger baseFeePerGas,
      BigInteger gasPrice) {

    FeeInputs {
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

  record FeePlan(BigInteger gasLimit, BigInteger maxPriorityFeePerGas, BigInteger maxFeePerGas) {

    FeePlan {
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
