package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.MarkQuestionRewardIntentSubmittedCommand;

public interface MarkQuestionRewardIntentSubmittedUseCase {

  void execute(MarkQuestionRewardIntentSubmittedCommand command);
}
