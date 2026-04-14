package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.BuildExecutionDigestPort;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.config.ExecutionEip712Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.utils.Numeric;

@Component
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class Eip7702ExecutionDigestAdapter implements BuildExecutionDigestPort {

  private final ExecutionEip712Properties executionEip712Properties;

  public Eip7702ExecutionDigestAdapter(ExecutionEip712Properties executionEip712Properties) {
    this.executionEip712Properties = executionEip712Properties;
  }

  @Override
  public String buildExecutionDigestHex(
      String authorityAddress,
      String executionIntentId,
      String callDataHash,
      BigInteger deadlineEpochSeconds) {
    byte[] digest =
        ExecutionTypedDataDigestBuilder.buildDigest(
            executionEip712Properties.getDomainName(),
            executionEip712Properties.getDomainVersion(),
            executionEip712Properties.getChainId(),
            authorityAddress,
            executionIntentId,
            callDataHash,
            deadlineEpochSeconds);
    return Numeric.toHexString(digest);
  }
}
