package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraft;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CreateTransferCommand;

public interface BuildTransferExecutionDraftPort {

  ExecutionDraft build(CreateTransferCommand command);
}
