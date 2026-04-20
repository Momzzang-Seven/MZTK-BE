package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentResult;

public interface ExecuteInternalExecutionIntentUseCase {

  ExecuteInternalExecutionIntentResult execute(ExecuteInternalExecutionIntentCommand command);
}
