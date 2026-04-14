package momzzangseven.mztkbe.modules.web3.eip7702.application.port.in;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionAuthorizationTuple;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionBatchCall;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionFeePlan;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionSignCommand;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionSignedPayload;

public interface ManageExecutionEip7702UseCase {

  boolean verifyAuthorizationSigner(
      long chainId,
      String delegateTarget,
      BigInteger nonce,
      String signatureHex,
      String expectedAddress);

  Eip7702ExecutionAuthorizationTuple toAuthorizationTuple(
      long chainId, String delegateTarget, BigInteger nonce, String signatureHex);

  BigInteger estimateGasWithAuthorization(
      String sponsorAddress,
      String authorityAddress,
      String data,
      List<Eip7702ExecutionAuthorizationTuple> authorizationList);

  Eip7702ExecutionFeePlan loadSponsorFeePlan();

  BigInteger loadPendingAccountNonce(String address);

  String hashCalls(List<Eip7702ExecutionBatchCall> calls);

  String encodeExecute(List<Eip7702ExecutionBatchCall> calls, byte[] executionSignature);

  Eip7702ExecutionSignedPayload signAndEncode(Eip7702ExecutionSignCommand command);

  boolean verifyExecutionSignature(
      String authorityAddress,
      String prepareId,
      String callDataHash,
      BigInteger deadlineEpochSeconds,
      String signatureHex);
}
