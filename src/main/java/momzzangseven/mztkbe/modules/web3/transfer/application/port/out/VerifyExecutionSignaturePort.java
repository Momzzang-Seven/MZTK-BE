package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.math.BigInteger;

/** Port for verifying EIP-712 Mztk7702Execution signature. */
public interface VerifyExecutionSignaturePort {

  boolean verify(
      String authorityAddress,
      String prepareId,
      String callDataHash,
      BigInteger gasLimit,
      BigInteger maxFeePerGas,
      BigInteger deadlineEpochSeconds,
      String signatureHex);
}
