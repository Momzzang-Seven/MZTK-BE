package momzzangseven.mztkbe.modules.web3.eip7702.application.port.out;

import java.math.BigInteger;

/** Port for verifying EIP-712 Mztk7702Execution signature. */
public interface VerifyExecutionSignaturePort {

  boolean verify(
      String authorityAddress,
      String prepareId,
      String callDataHash,
      BigInteger deadlineEpochSeconds,
      String signatureHex);
}
