package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.adapter.Eip7702ExecutionTypedDataHelper;
import momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config.Eip712Properties;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.BuildExecutionDigestPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.utils.Numeric;

@Component
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class Eip7702ExecutionDigestAdapter implements BuildExecutionDigestPort {

  private final Eip712Properties eip712Properties;

  public Eip7702ExecutionDigestAdapter(Eip712Properties eip712Properties) {
    this.eip712Properties = eip712Properties;
  }

  @Override
  public String buildExecutionDigestHex(
      String authorityAddress,
      String executionIntentId,
      String callDataHash,
      BigInteger deadlineEpochSeconds) {
    byte[] digest =
        Eip7702ExecutionTypedDataHelper.buildDigest(
            eip712Properties.getDomainName(),
            eip712Properties.getDomainVersion(),
            eip712Properties.getChainId(),
            authorityAddress,
            executionIntentId,
            callDataHash,
            deadlineEpochSeconds);
    return Numeric.toHexString(digest);
  }
}
