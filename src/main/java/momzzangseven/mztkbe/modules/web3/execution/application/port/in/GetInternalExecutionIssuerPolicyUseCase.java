package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.InternalExecutionIssuerPolicyView;

public interface GetInternalExecutionIssuerPolicyUseCase {

  InternalExecutionIssuerPolicyView getPolicy();
}
