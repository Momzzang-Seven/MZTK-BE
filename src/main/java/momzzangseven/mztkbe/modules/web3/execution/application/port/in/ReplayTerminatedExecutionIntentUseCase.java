package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayTerminatedExecutionIntentCommand;

public interface ReplayTerminatedExecutionIntentUseCase {

  boolean execute(ReplayTerminatedExecutionIntentCommand command);
}
