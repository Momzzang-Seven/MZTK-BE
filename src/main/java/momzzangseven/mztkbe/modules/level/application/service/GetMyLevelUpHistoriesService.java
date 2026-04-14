package momzzangseven.mztkbe.modules.level.application.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.modules.level.application.dto.GetMyLevelUpHistoriesResult;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpHistoryItem;
import momzzangseven.mztkbe.modules.level.application.port.in.GetMyLevelUpHistoriesUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.LevelUpHistoryPort;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadLevelRewardTransactionPort;
import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardStatus;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxPhase;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetMyLevelUpHistoriesService implements GetMyLevelUpHistoriesUseCase {

  private static final int MAX_PAGE_SIZE = 100;

  private final LevelUpHistoryPort levelUpHistoryPort;
  private final LoadLevelRewardTransactionPort loadLevelRewardTransactionPort;

  @Value("${web3.explorer.base-url:}")
  private String explorerBaseUrl;

  @Override
  public GetMyLevelUpHistoriesResult execute(Long userId, int page, int size) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0");
    }
    if (size <= 0 || size > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
    }

    List<LevelUpHistory> loadedHistories =
        levelUpHistoryPort.loadLevelUpHistories(userId, page, size);
    boolean hasNext = loadedHistories.size() > size;
    List<LevelUpHistory> pageHistories =
        hasNext ? loadedHistories.subList(0, size) : loadedHistories;
    Map<Long, LoadLevelRewardTransactionPort.RewardTxView> rewardTxViews =
        loadLevelRewardTransactionPort.loadByLevelUpHistoryIds(
            pageHistories.stream().map(LevelUpHistory::getId).toList());
    List<LevelUpHistoryItem> items =
        pageHistories.stream()
            .map(history -> mapToItem(history, rewardTxViews.get(history.getId())))
            .toList();

    return GetMyLevelUpHistoriesResult.builder()
        .page(page)
        .size(size)
        .hasNext(hasNext)
        .histories(items)
        .build();
  }

  private LevelUpHistoryItem mapToItem(
      LevelUpHistory history, LoadLevelRewardTransactionPort.RewardTxView rewardTxView) {
    RewardTxStatus rewardTxStatus = resolveRewardTxStatus(history, rewardTxView);
    RewardStatus rewardStatus = toLegacyStatus(rewardTxStatus);
    String rewardTxHash = rewardTxView != null ? rewardTxView.txHash() : null;

    return LevelUpHistoryItem.builder()
        .levelUpHistoryId(history.getId())
        .fromLevel(history.getFromLevel())
        .toLevel(history.getToLevel())
        .spentXp(history.getSpentXp())
        .rewardMztk(history.getRewardMztk())
        .rewardStatus(rewardStatus)
        .rewardTxStatus(rewardTxStatus)
        .rewardTxPhase(RewardTxPhase.from(rewardTxStatus))
        .rewardTxHash(rewardTxHash)
        .rewardExplorerUrl(buildExplorerUrl(rewardTxHash))
        .createdAt(history.getCreatedAt())
        .build();
  }

  private RewardTxStatus resolveRewardTxStatus(
      LevelUpHistory history, LoadLevelRewardTransactionPort.RewardTxView rewardTxView) {
    if (rewardTxView != null) {
      return rewardTxView.status();
    }
    if (history.getRewardMztk() <= 0) {
      return RewardTxStatus.SUCCEEDED;
    }
    return RewardTxStatus.CREATED;
  }

  private RewardStatus toLegacyStatus(RewardTxStatus status) {
    if (status == RewardTxStatus.SUCCEEDED) {
      return RewardStatus.SUCCESS;
    }
    if (status == RewardTxStatus.FAILED_ONCHAIN) {
      return RewardStatus.FAILED;
    }
    return RewardStatus.PENDING;
  }

  private String buildExplorerUrl(String txHash) {
    if (txHash == null || txHash.isBlank()) {
      return null;
    }
    if (explorerBaseUrl == null || explorerBaseUrl.isBlank()) {
      return null;
    }
    return explorerBaseUrl + "/tx/" + txHash;
  }
}
