package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3Transaction;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CreateLevelUpRewardTxIntentCommand;

/** Port for idempotent intent persistence to web3_transactions. */
public interface SaveTransactionPort {
  Web3Transaction saveLevelUpRewardIntent(CreateLevelUpRewardTxIntentCommand command);
}
