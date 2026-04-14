package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.math.BigInteger;

public interface BuildExecutionDigestPort {

  String buildExecutionDigestHex(
      String authorityAddress,
      String executionIntentId,
      String callDataHash,
      BigInteger deadlineEpochSeconds);
}
