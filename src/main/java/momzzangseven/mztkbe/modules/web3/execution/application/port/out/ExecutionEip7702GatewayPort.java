package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.math.BigInteger;
import java.util.List;

public interface ExecutionEip7702GatewayPort {

  boolean verifyAuthorizationSigner(
      long chainId,
      String delegateTarget,
      BigInteger nonce,
      String signatureHex,
      String expectedAddress);

  AuthorizationTuple toAuthorizationTuple(
      long chainId, String delegateTarget, BigInteger nonce, String signatureHex);

  BigInteger estimateGasWithAuthorization(
      String sponsorAddress,
      String authorityAddress,
      String data,
      List<AuthorizationTuple> authorizationList);

  FeePlan loadSponsorFeePlan();

  BigInteger loadPendingAccountNonce(String address);

  String hashCalls(List<BatchCall> calls);

  String encodeExecute(List<BatchCall> calls, String executionSignatureHex);

  SignedPayload signAndEncode(SignCommand command);

  boolean verifyExecutionSignature(
      String authorityAddress,
      String prepareId,
      String callDataHash,
      BigInteger deadlineEpochSeconds,
      String signatureHex);

  record AuthorizationTuple(
      BigInteger chainId,
      String address,
      BigInteger nonce,
      BigInteger yParity,
      BigInteger r,
      BigInteger s) {}

  record FeePlan(BigInteger maxPriorityFeePerGas, BigInteger maxFeePerGas) {}

  record BatchCall(String to, BigInteger value, String dataHex) {}

  record SignCommand(
      long chainId,
      BigInteger nonce,
      BigInteger maxPriorityFeePerGas,
      BigInteger maxFeePerGas,
      BigInteger gasLimit,
      String to,
      BigInteger value,
      String data,
      List<AuthorizationTuple> authorizationList,
      String sponsorPrivateKeyHex) {}

  record SignedPayload(String rawTx, String txHash) {}
}
