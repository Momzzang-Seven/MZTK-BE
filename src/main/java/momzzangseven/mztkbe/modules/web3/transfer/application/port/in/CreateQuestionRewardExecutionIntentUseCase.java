package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;

public interface CreateQuestionRewardExecutionIntentUseCase {

  CreateExecutionIntentResult execute(RegisterQuestionRewardIntentCommand command);
}
