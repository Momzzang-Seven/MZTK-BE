package momzzangseven.mztkbe.modules.web3.transaction.application.port.in;

import java.util.Collection;
import java.util.Map;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

/** Inbound query use case for LEVEL_UP_REWARD transaction states. */
public interface LoadLevelRewardTransactionsUseCase {

  Map<Long, RewardTxView> loadByLevelUpHistoryIds(Collection<Long> levelUpHistoryIds);

  record RewardTxView(Web3TxStatus status, String txHash) {}
}
