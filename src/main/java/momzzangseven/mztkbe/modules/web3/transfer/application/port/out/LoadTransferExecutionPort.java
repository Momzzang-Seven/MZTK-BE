package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;

public interface LoadTransferExecutionPort {

  TransferExecutionIntentResult load(Long requesterUserId, String executionIntentId);
}
