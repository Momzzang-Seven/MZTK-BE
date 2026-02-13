package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.Web3InvalidInputException;

/** Strategy for selecting gas/fee values used by signing and broadcast prevalidation. */
public interface GasFeeStrategy {

  FeePlan calculate(FeeInputs inputs);

  record FeeInputs(
      BigInteger estimatedGas,
      BigInteger maxPriorityFeePerGas,
      BigInteger baseFeePerGas,
      BigInteger gasPrice) {

    public FeeInputs {
      validate(estimatedGas, maxPriorityFeePerGas, baseFeePerGas, gasPrice);
    }

    private static void validate(
        BigInteger estimatedGas,
        BigInteger maxPriorityFeePerGas,
        BigInteger baseFeePerGas,
        BigInteger gasPrice) {
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
