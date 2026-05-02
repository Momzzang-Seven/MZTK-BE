package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.eip7702;

import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionAuthorizationTuple;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionBatchCall;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionFeePlan;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.ManageExecutionEip7702UseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip7702GatewayPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnUserExecutionEnabled;
import org.springframework.stereotype.Component;

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
            .map(call -> new Eip7702ExecutionBatchCall(call.to(), call.value(), call.data()))
            .toList());
  }

  @Override
  public String encodeExecute(List<BatchCall> calls, byte[] executionSignature) {
    return manageExecutionEip7702UseCase.encodeExecute(
        calls.stream()
            .map(call -> new Eip7702ExecutionBatchCall(call.to(), call.value(), call.data()))
            .toList(),
        executionSignature);
  }

  @Override
  public SignedPayload signAndEncode(SignCommand command) {
    // Transitional shim: 3-3 swapped Eip7702ExecutionSignCommand to TreasurySigner-based signing,
    // but the execution-side SignCommand still carries sponsorPrivateKeyHex until commit 3-4 wires
    // a TreasurySigner through ExecuteExecutionIntentService. Fail loudly rather than fabricate a
    // bogus TreasurySigner from the legacy private-key string.
    throw new UnsupportedOperationException(
        "KMS sponsor signing rewiring lands in commit 3-4; ExecutionEip7702GatewayPort.SignCommand"
            + " must carry a TreasurySigner before this adapter can construct"
            + " Eip7702ExecutionSignCommand");
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
