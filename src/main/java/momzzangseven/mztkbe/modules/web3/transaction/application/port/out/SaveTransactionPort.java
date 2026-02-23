package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import momzzangseven.mztkbe.modules.web3.transaction.application.dto.CreateLevelUpRewardTxIntentCommand;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3Transaction;

/** Port for idempotent intent persistence to web3_transactions. */
public interface SaveTransactionPort {
  Web3Transaction saveLevelUpRewardIntent(CreateLevelUpRewardTxIntentCommand command);
}
