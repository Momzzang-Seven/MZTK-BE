package momzzangseven.mztkbe.modules.web3.execution.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.Eip7702AuthorizationPolicyResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetEip7702AuthorizationPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadEip7702AuthorizationTtlPort;

@RequiredArgsConstructor
public class GetEip7702AuthorizationPolicyService implements GetEip7702AuthorizationPolicyUseCase {

  private final LoadEip7702AuthorizationTtlPort loadEip7702AuthorizationTtlPort;

  @Override
  public Eip7702AuthorizationPolicyResult execute() {
    return new Eip7702AuthorizationPolicyResult(
        loadEip7702AuthorizationTtlPort.loadMinimumRemainingSeconds());
  }
}
