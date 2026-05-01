package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.eip7702;

import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionAuthorizationTuple;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionBatchCall;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionFeePlan;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionSignCommand;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionSignedPayload;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.ManageExecutionEip7702UseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip7702GatewayPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnUserExecutionEnabled;
import org.springframework.stereotype.Component;
import org.web3j.utils.Numeric;

@Component
@RequiredArgsConstructor
@ConditionalOnUserExecutionEnabled
public class ExecutionEip7702GatewayAdapter implements ExecutionEip7702GatewayPort {

  private final ManageExecutionEip7702UseCase manageExecutionEip7702UseCase;

  @Override
  public boolean verifyAuthorizationSigner(
      long chainId,
      String delegateTarget,
      BigInteger nonce,
      String signatureHex,
      String expectedAddress) {
    return manageExecutionEip7702UseCase.verifyAuthorizationSigner(
        chainId, delegateTarget, nonce, signatureHex, expectedAddress);
  }

  @Override
  public AuthorizationTuple toAuthorizationTuple(
      long chainId, String delegateTarget, BigInteger nonce, String signatureHex) {
    Eip7702ExecutionAuthorizationTuple tuple =
        manageExecutionEip7702UseCase.toAuthorizationTuple(
            chainId, delegateTarget, nonce, signatureHex);
    return new AuthorizationTuple(
        tuple.chainId(), tuple.address(), tuple.nonce(), tuple.yParity(), tuple.r(), tuple.s());
  }

  @Override
  public BigInteger estimateGasWithAuthorization(
      String sponsorAddress,
      String authorityAddress,
      String data,
      List<AuthorizationTuple> authorizationList) {
    return manageExecutionEip7702UseCase.estimateGasWithAuthorization(
        sponsorAddress,
        authorityAddress,
        data,
        authorizationList.stream()
            .map(
                tuple ->
                    new Eip7702ExecutionAuthorizationTuple(
                        tuple.chainId(),
                        tuple.address(),
                        tuple.nonce(),
                        tuple.yParity(),
                        tuple.r(),
                        tuple.s()))
            .toList());
  }

  @Override
  public FeePlan loadSponsorFeePlan() {
    Eip7702ExecutionFeePlan feePlan = manageExecutionEip7702UseCase.loadSponsorFeePlan();
    return new FeePlan(feePlan.maxPriorityFeePerGas(), feePlan.maxFeePerGas());
  }

  @Override
  public BigInteger loadPendingAccountNonce(String address) {
    return manageExecutionEip7702UseCase.loadPendingAccountNonce(address);
  }

  @Override
  public String hashCalls(List<BatchCall> calls) {
    return manageExecutionEip7702UseCase.hashCalls(
        calls.stream()
            .map(
                call ->
                    new Eip7702ExecutionBatchCall(
                        call.to(), call.value(), Numeric.hexStringToByteArray(call.data())))
            .toList());
  }

  @Override
  public String encodeExecute(List<BatchCall> calls, String executionSignatureHex) {
    return manageExecutionEip7702UseCase.encodeExecute(
        calls.stream()
            .map(
                call ->
                    new Eip7702ExecutionBatchCall(
                        call.to(), call.value(), Numeric.hexStringToByteArray(call.data())))
            .toList(),
        Numeric.hexStringToByteArray(executionSignatureHex));
  }

  @Override
  public SignedPayload signAndEncode(SignCommand command) {
    Eip7702ExecutionSignedPayload signedPayload =
        manageExecutionEip7702UseCase.signAndEncode(
            new Eip7702ExecutionSignCommand(
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
                            new Eip7702ExecutionAuthorizationTuple(
                                tuple.chainId(),
                                tuple.address(),
                                tuple.nonce(),
                                tuple.yParity(),
                                tuple.r(),
                                tuple.s()))
                    .toList(),
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
    return manageExecutionEip7702UseCase.verifyExecutionSignature(
        authorityAddress, prepareId, callDataHash, deadlineEpochSeconds, signatureHex);
  }
}
