package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraft;

public interface SubmitExecutionDraftPort {

  CreateExecutionIntentResult submit(ExecutionDraft draft);
}
