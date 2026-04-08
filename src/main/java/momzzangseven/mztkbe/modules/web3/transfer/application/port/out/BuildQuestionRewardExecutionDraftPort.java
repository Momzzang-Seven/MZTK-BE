package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionDraft;

public interface BuildQuestionRewardExecutionDraftPort {

  TransferExecutionDraft build(RegisterQuestionRewardIntentCommand command);
}
