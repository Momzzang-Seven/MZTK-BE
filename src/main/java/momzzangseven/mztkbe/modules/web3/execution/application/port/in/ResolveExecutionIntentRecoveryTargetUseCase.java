package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ResolveExecutionIntentRecoveryTargetQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ResolveExecutionIntentRecoveryTargetResult;

public interface ResolveExecutionIntentRecoveryTargetUseCase {

  Optional<ResolveExecutionIntentRecoveryTargetResult> execute(
      ResolveExecutionIntentRecoveryTargetQuery query);
}
