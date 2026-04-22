package momzzangseven.mztkbe.modules.web3.eip7702.application.service;

import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionAuthorizationTuple;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionBatchCall;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionFeePlan;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionSignCommand;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionSignedPayload;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.ManageExecutionEip7702UseCase;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.VerifyExecutionSignaturePort;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManageExecutionEip7702Service implements ManageExecutionEip7702UseCase {

  private final Eip7702AuthorizationPort eip7702AuthorizationPort;
  private final Eip7702ChainPort eip7702ChainPort;
  private final Eip7702TransactionCodecPort eip7702TransactionCodecPort;
  private final VerifyExecutionSignaturePort verifyExecutionSignaturePort;

  @Override
  public boolean verifyAuthorizationSigner(
      long chainId,
      String delegateTarget,
      BigInteger nonce,
      String signatureHex,
      String expectedAddress) {
    return eip7702AuthorizationPort.verifySigner(
        chainId, delegateTarget, nonce, signatureHex, expectedAddress);
  }

  @Override
  public Eip7702ExecutionAuthorizationTuple toAuthorizationTuple(
      long chainId, String delegateTarget, BigInteger nonce, String signatureHex) {
    Eip7702ChainPort.AuthorizationTuple tuple =
        eip7702AuthorizationPort.toAuthorizationTuple(chainId, delegateTarget, nonce, signatureHex);
    return new Eip7702ExecutionAuthorizationTuple(
        tuple.chainId(), tuple.address(), tuple.nonce(), tuple.yParity(), tuple.r(), tuple.s());
  }

  @Override
  public BigInteger estimateGasWithAuthorization(
      String sponsorAddress,
      String authorityAddress,
      String data,
      List<Eip7702ExecutionAuthorizationTuple> authorizationList) {
    return eip7702ChainPort.estimateGasWithAuthorization(
        sponsorAddress,
        authorityAddress,
        data,
        authorizationList.stream()
            .map(
                tuple ->
                    new Eip7702ChainPort.AuthorizationTuple(
                        tuple.chainId(),
                        tuple.address(),
                        tuple.nonce(),
                        tuple.yParity(),
                        tuple.r(),
                        tuple.s()))
            .toList());
  }

  @Override
  public Eip7702ExecutionFeePlan loadSponsorFeePlan() {
    Eip7702ChainPort.FeePlan feePlan = eip7702ChainPort.loadSponsorFeePlan();
    return new Eip7702ExecutionFeePlan(feePlan.maxPriorityFeePerGas(), feePlan.maxFeePerGas());
  }

  @Override
  public BigInteger loadPendingAccountNonce(String address) {
    return eip7702ChainPort.loadPendingAccountNonce(address);
  }

  @Override
  public String hashCalls(List<Eip7702ExecutionBatchCall> calls) {
    return eip7702TransactionCodecPort.hashCalls(
        calls.stream()
            .map(
                call ->
                    new Eip7702TransactionCodecPort.BatchCall(call.to(), call.value(), call.data()))
            .toList());
  }

  @Override
  public String encodeExecute(List<Eip7702ExecutionBatchCall> calls, byte[] executionSignature) {
    return eip7702TransactionCodecPort.encodeExecute(
        calls.stream()
            .map(
                call ->
                    new Eip7702TransactionCodecPort.BatchCall(call.to(), call.value(), call.data()))
            .toList(),
        executionSignature);
  }

  @Override
  public Eip7702ExecutionSignedPayload signAndEncode(Eip7702ExecutionSignCommand command) {
    Eip7702TransactionCodecPort.SignedPayload signedPayload =
        eip7702TransactionCodecPort.signAndEncode(
            new Eip7702TransactionCodecPort.SignCommand(
                command.chainId(),
                command.nonce(),
                command.maxPriorityFeePerGas(),
                command.maxFeePerGas(),
                command.gasLimit(),
                command.to(),
                command.value(),
                command.data(),
                command.authorizationList().stream()
                    .map(
                        tuple ->
                            new Eip7702ChainPort.AuthorizationTuple(
                                tuple.chainId(),
                                tuple.address(),
                                tuple.nonce(),
                                tuple.yParity(),
                                tuple.r(),
                                tuple.s()))
                    .toList(),
                command.sponsorPrivateKeyHex()));
    return new Eip7702ExecutionSignedPayload(signedPayload.rawTx(), signedPayload.txHash());
  }

  @Override
  public boolean verifyExecutionSignature(
      String authorityAddress,
      String prepareId,
      String callDataHash,
      BigInteger deadlineEpochSeconds,
      String signatureHex) {
    return verifyExecutionSignaturePort.verify(
        authorityAddress, prepareId, callDataHash, deadlineEpochSeconds, signatureHex);
  }
}
