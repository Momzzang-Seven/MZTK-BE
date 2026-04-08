package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraft;

/** Output port for submitting prepared execution drafts to shared execution module. */
public interface SubmitExecutionDraftPort {

  /** Submits transfer-owned draft and returns created/reused execution intent contract. */
  CreateExecutionIntentResult submit(ExecutionDraft draft);
}
