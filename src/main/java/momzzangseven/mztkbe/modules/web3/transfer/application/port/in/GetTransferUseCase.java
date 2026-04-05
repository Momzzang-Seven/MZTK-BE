package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.GetTransferQuery;

public interface GetTransferUseCase {

  GetExecutionIntentResult execute(GetTransferQuery query);
}
