package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.LoadLevelRewardTransactionsUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoadLevelRewardTransactionsService implements LoadLevelRewardTransactionsUseCase {

  private final LoadTransactionPort loadTransactionPort;

  @Override
  public Map<Long, RewardTxView> loadByLevelUpHistoryIds(Collection<Long> levelUpHistoryIds) {
    if (levelUpHistoryIds == null || levelUpHistoryIds.isEmpty()) {
      return Map.of();
    }

    var referenceIds = levelUpHistoryIds.stream().map(String::valueOf).toList();
    Map<Long, RewardTxView> views = new LinkedHashMap<>();

    loadTransactionPort
        .loadLevelRewardsByReferenceIds(referenceIds)
        .forEach(
            snapshot ->
                views.put(
                    parseLevelUpHistoryId(snapshot.referenceId()),
                    new RewardTxView(snapshot.status(), snapshot.txHash())));

    return views;
  }

  private Long parseLevelUpHistoryId(String referenceId) {
    try {
      return Long.parseLong(referenceId);
    } catch (NumberFormatException e) {
      throw new Web3InvalidInputException("invalid LEVEL_UP_REWARD referenceId: " + referenceId);
    }
  }
}
