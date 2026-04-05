package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CreateTransferCommand;

public interface CreateTransferUseCase {

  CreateExecutionIntentResult execute(CreateTransferCommand command);
}
