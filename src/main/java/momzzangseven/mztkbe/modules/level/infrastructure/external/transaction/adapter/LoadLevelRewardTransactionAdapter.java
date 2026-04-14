package momzzangseven.mztkbe.modules.level.infrastructure.external.transaction.adapter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadLevelRewardTransactionPort;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.LoadLevelRewardTransactionsUseCase;
import org.springframework.stereotype.Component;

/** Adapter from level module to transaction query use case. */
@Component
@RequiredArgsConstructor
public class LoadLevelRewardTransactionAdapter implements LoadLevelRewardTransactionPort {

  private final LoadLevelRewardTransactionsUseCase loadLevelRewardTransactionsUseCase;

  @Override
  public Map<Long, RewardTxView> loadByLevelUpHistoryIds(Collection<Long> levelUpHistoryIds) {
    Map<Long, LoadLevelRewardTransactionsUseCase.RewardTxView> transactionViews =
        loadLevelRewardTransactionsUseCase.loadByLevelUpHistoryIds(levelUpHistoryIds);

    Map<Long, RewardTxView> levelViews = new LinkedHashMap<>();
    transactionViews.forEach(
        (levelUpHistoryId, view) ->
            levelViews.put(
                levelUpHistoryId,
                new RewardTxView(toRewardTxStatus(view.status().name()), view.txHash())));
    return levelViews;
  }

  private RewardTxStatus toRewardTxStatus(String statusName) {
    return RewardTxStatus.valueOf(statusName);
  }
}
