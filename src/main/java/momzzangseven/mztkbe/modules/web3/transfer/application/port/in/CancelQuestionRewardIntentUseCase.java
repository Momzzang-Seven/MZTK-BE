package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CancelQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CancelQuestionRewardIntentResult;

public interface CancelQuestionRewardIntentUseCase {
  CancelQuestionRewardIntentResult execute(CancelQuestionRewardIntentCommand command);
}
