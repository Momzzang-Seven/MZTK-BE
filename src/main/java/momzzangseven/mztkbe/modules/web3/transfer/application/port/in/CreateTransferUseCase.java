package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CreateTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;

public interface CreateTransferUseCase {

  TransferExecutionIntentResult execute(CreateTransferCommand command);
}
