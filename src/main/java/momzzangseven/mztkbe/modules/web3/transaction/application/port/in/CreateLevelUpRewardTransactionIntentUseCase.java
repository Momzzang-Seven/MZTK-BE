package momzzangseven.mztkbe.modules.web3.transaction.application.port.in;

import momzzangseven.mztkbe.modules.web3.transaction.application.dto.CreateLevelUpRewardTransactionIntentCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.CreateLevelUpRewardTransactionIntentResult;

public interface CreateLevelUpRewardTransactionIntentUseCase {

  CreateLevelUpRewardTransactionIntentResult execute(
      CreateLevelUpRewardTransactionIntentCommand command);
}
