package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayConfirmedExecutionIntentCommand;

public interface ReplayConfirmedExecutionIntentUseCase {

  boolean execute(ReplayConfirmedExecutionIntentCommand command);
}
