package momzzangseven.mztkbe.modules.level.application.port.out;

import java.util.Collection;
import java.util.Map;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;

/** Outbound port for loading reward tx state by level-up history IDs. */
public interface LoadLevelRewardTransactionPort {

  Map<Long, RewardTxView> loadByLevelUpHistoryIds(Collection<Long> levelUpHistoryIds);

  record RewardTxView(RewardTxStatus status, String txHash) {}
}
