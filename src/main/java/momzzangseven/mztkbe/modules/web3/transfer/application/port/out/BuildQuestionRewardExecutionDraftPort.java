package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraft;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;

public interface BuildQuestionRewardExecutionDraftPort {

  ExecutionDraft build(RegisterQuestionRewardIntentCommand command);
}
