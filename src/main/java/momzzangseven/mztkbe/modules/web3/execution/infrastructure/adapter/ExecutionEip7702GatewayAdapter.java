package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.VerifyExecutionSignaturePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip7702GatewayPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionEip7702GatewayAdapter implements ExecutionEip7702GatewayPort {

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
  public AuthorizationTuple toAuthorizationTuple(
      long chainId, String delegateTarget, BigInteger nonce, String signatureHex) {
    Eip7702ChainPort.AuthorizationTuple tuple =
        eip7702AuthorizationPort.toAuthorizationTuple(chainId, delegateTarget, nonce, signatureHex);
    return new AuthorizationTuple(
        tuple.chainId(), tuple.address(), tuple.nonce(), tuple.yParity(), tuple.r(), tuple.s());
  }

  @Override
  public BigInteger estimateGasWithAuthorization(
      String sponsorAddress,
      String authorityAddress,
      String data,
      List<AuthorizationTuple> authorizationList) {
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
            .collect(Collectors.toList()));
  }

  @Override
  public FeePlan loadSponsorFeePlan() {
    Eip7702ChainPort.FeePlan feePlan = eip7702ChainPort.loadSponsorFeePlan();
    return new FeePlan(feePlan.maxPriorityFeePerGas(), feePlan.maxFeePerGas());
  }

  @Override
  public BigInteger loadPendingAccountNonce(String address) {
    return eip7702ChainPort.loadPendingAccountNonce(address);
  }

  @Override
  public String hashCalls(List<BatchCall> calls) {
    return eip7702TransactionCodecPort.hashCalls(
        calls.stream()
            .map(call -> new Eip7702TransactionCodecPort.BatchCall(call.to(), call.value(), call.data()))
            .collect(Collectors.toList()));
  }

  @Override
  public String encodeExecute(List<BatchCall> calls, byte[] executionSignature) {
    return eip7702TransactionCodecPort.encodeExecute(
        calls.stream()
            .map(call -> new Eip7702TransactionCodecPort.BatchCall(call.to(), call.value(), call.data()))
            .collect(Collectors.toList()),
        executionSignature);
  }

  @Override
  public SignedPayload signAndEncode(SignCommand command) {
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
                    .collect(Collectors.toList()),
                command.sponsorPrivateKeyHex()));
    return new SignedPayload(signedPayload.rawTx(), signedPayload.txHash());
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
