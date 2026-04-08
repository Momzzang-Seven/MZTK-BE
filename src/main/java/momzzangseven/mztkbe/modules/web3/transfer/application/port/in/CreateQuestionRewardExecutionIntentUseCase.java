package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;

public interface CreateQuestionRewardExecutionIntentUseCase {

  TransferExecutionIntentResult execute(RegisterQuestionRewardIntentCommand command);
}
