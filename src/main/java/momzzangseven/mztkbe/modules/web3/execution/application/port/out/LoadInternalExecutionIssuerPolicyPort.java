package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;

public interface LoadInternalExecutionIssuerPolicyPort {

  InternalExecutionIssuerPolicy loadPolicy();

  record InternalExecutionIssuerPolicy(
      boolean enabled, int batchSize, List<ExecutionActionType> actionTypes) {}
}
