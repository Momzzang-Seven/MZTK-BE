package momzzangseven.mztkbe.modules.web3.token.application.port.out;

import java.math.BigInteger;

/** Strategy for selecting gas/fee values used by signing and broadcast prevalidation. */
public interface GasFeeStrategy {

  FeePlan calculate(FeeInputs inputs);

  record FeeInputs(
      BigInteger estimatedGas,
      BigInteger maxPriorityFeePerGas,
      BigInteger baseFeePerGas,
      BigInteger gasPrice) {}

  record FeePlan(BigInteger gasLimit, BigInteger maxPriorityFeePerGas, BigInteger maxFeePerGas) {}
}
