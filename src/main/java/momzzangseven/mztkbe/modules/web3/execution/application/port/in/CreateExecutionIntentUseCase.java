package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;

public interface CreateExecutionIntentUseCase {

  CreateExecutionIntentResult execute(CreateExecutionIntentCommand command);
}
