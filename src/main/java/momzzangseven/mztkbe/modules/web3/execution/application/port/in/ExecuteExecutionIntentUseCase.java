package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;

public interface ExecuteExecutionIntentUseCase {

  ExecuteExecutionIntentResult execute(ExecuteExecutionIntentCommand command);
}
