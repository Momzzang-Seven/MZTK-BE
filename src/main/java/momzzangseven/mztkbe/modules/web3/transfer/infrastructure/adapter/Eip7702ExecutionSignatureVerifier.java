package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.signature.infrastructure.config.EIP712Properties;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.VerifyExecutionSignaturePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Verifies Mztk7702Execution EIP-712 signature with dynamic verifyingContract(authority). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class Eip7702ExecutionSignatureVerifier implements VerifyExecutionSignaturePort {

  private final EIP712Properties eip712Properties;

  @Override
  public boolean verify(
      String authorityAddress,
      String prepareId,
      String callDataHash,
      BigInteger deadlineEpochSeconds,
      String signatureHex) {
    byte[] digest =
        Eip7702ExecutionTypedDataHelper.buildDigest(
            eip712Properties.getDomainName(),
            eip712Properties.getDomainVersion(),
            eip712Properties.getChainId(),
            authorityAddress,
            prepareId,
            callDataHash,
            deadlineEpochSeconds);
    String recoveredAddress = Eip7702AuthorizationHelper.recoverAddress(digest, signatureHex);
    return recoveredAddress.equalsIgnoreCase(authorityAddress);
  }
}
