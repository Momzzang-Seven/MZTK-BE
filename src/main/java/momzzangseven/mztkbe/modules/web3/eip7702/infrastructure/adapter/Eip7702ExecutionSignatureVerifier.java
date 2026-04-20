package momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.adapter;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3ConfigInvalidException;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.VerifyExecutionSignaturePort;
import momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config.Eip712Properties;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnUserExecutionEnabled;
import org.springframework.stereotype.Component;

/** Verifies Mztk7702Execution EIP-712 signature with dynamic verifyingContract(authority). */
@Component
@RequiredArgsConstructor
@ConditionalOnUserExecutionEnabled
public class Eip7702ExecutionSignatureVerifier implements VerifyExecutionSignaturePort {

  private final Eip712Properties eip712Properties;

  @Override
  public boolean verify(
      String authorityAddress,
      String executionIntentId,
      String callDataHash,
      BigInteger deadlineEpochSeconds,
      String signatureHex) {
    Long chainId = eip712Properties.getChainId();
    if (chainId == null) {
      throw new Web3ConfigInvalidException("web3.eip712.chain-id is required");
    }
    byte[] digest =
        Eip7702ExecutionTypedDataHelper.buildDigest(
            eip712Properties.getDomainName(),
            eip712Properties.getDomainVersion(),
            chainId,
            authorityAddress,
            executionIntentId,
            callDataHash,
            deadlineEpochSeconds);
    String recoveredAddress = Eip7702AuthorizationHelper.recoverAddress(digest, signatureHex);
    return recoveredAddress.equalsIgnoreCase(authorityAddress);
  }
}
