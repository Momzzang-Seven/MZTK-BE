package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.RunExecutionTerminationHookCommand;

public interface RunExecutionTerminationHookUseCase {

  void execute(RunExecutionTerminationHookCommand command);
}
