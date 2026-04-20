package momzzangseven.mztkbe.modules.web3.execution.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.InternalExecutionIssuerPolicyView;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetInternalExecutionIssuerPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;

@RequiredArgsConstructor
public class GetInternalExecutionIssuerPolicyService
    implements GetInternalExecutionIssuerPolicyUseCase {

  private final LoadInternalExecutionIssuerPolicyPort loadInternalExecutionIssuerPolicyPort;

  @Override
  public InternalExecutionIssuerPolicyView getPolicy() {
    LoadInternalExecutionIssuerPolicyPort.InternalExecutionIssuerPolicy policy =
        loadInternalExecutionIssuerPolicyPort.loadPolicy();
    return new InternalExecutionIssuerPolicyView(
        policy.enabled(),
        policy.actionTypes().contains(ExecutionActionType.QNA_ADMIN_SETTLE),
        policy.actionTypes().contains(ExecutionActionType.QNA_ADMIN_REFUND));
  }
}
