package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CreateTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionDraft;

public interface BuildTransferExecutionDraftPort {

  TransferExecutionDraft build(CreateTransferCommand command);
}
