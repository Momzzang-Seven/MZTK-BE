package momzzangseven.mztkbe.modules.level.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.modules.level.application.dto.GetMyLevelUpHistoriesResult;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpHistoryItem;
import momzzangseven.mztkbe.modules.level.application.port.in.GetMyLevelUpHistoriesUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.LevelUpHistoryPort;
import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetMyLevelUpHistoriesService implements GetMyLevelUpHistoriesUseCase {

  private static final int MAX_PAGE_SIZE = 100;

  private final LevelUpHistoryPort levelUpHistoryPort;

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

    List<LevelUpHistory> loadedHistories = levelUpHistoryPort.loadLevelUpHistories(userId, page, size);
    boolean hasNext = loadedHistories.size() > size;
    List<LevelUpHistory> pageHistories = hasNext ? loadedHistories.subList(0, size) : loadedHistories;
    List<LevelUpHistoryItem> items = pageHistories.stream().map(this::mapToItem).toList();

    return GetMyLevelUpHistoriesResult.builder()
        .page(page)
        .size(size)
        .hasNext(hasNext)
        .histories(items)
        .build();
  }

  private LevelUpHistoryItem mapToItem(LevelUpHistory history) {
    return LevelUpHistoryItem.builder()
        .levelUpHistoryId(history.getId())
        .fromLevel(history.getFromLevel())
        .toLevel(history.getToLevel())
        .spentXp(history.getSpentXp())
        .rewardMztk(history.getRewardMztk())
        .rewardStatus(history.getRewardStatus())
        .rewardTxHash(history.getRewardTxHash())
        .createdAt(history.getCreatedAt())
        .build();
  }
}
