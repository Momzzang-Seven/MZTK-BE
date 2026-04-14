package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.GetTransferQuery;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;

public interface GetTransferUseCase {

  TransferExecutionIntentResult execute(GetTransferQuery query);
}
