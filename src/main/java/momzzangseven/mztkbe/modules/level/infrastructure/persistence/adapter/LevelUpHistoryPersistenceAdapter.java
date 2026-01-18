package momzzangseven.mztkbe.modules.level.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpHistoriesSlice;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadLevelUpHistoriesPort;
import momzzangseven.mztkbe.modules.level.application.port.out.SaveLevelUpHistoryPort;
import momzzangseven.mztkbe.modules.level.application.port.out.UpdateLevelUpHistoryRewardPort;
import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;
import momzzangseven.mztkbe.modules.level.domain.model.RewardStatus;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.LevelUpHistoryEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.repository.LevelUpHistoryJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LevelUpHistoryPersistenceAdapter
    implements SaveLevelUpHistoryPort, UpdateLevelUpHistoryRewardPort, LoadLevelUpHistoriesPort {

  private final LevelUpHistoryJpaRepository levelUpHistoryJpaRepository;

  @Override
  @Transactional
  public LevelUpHistory saveLevelUpHistory(LevelUpHistory history) {
    LevelUpHistoryEntity saved = levelUpHistoryJpaRepository.saveAndFlush(mapToEntity(history));
    return mapToDomain(saved);
  }

  @Override
  @Transactional
  public void updateReward(Long levelUpHistoryId, RewardStatus status, String txHash) {
    LevelUpHistoryEntity entity =
        levelUpHistoryJpaRepository
            .findById(levelUpHistoryId)
            .orElseThrow(
                () ->
                    new IllegalStateException("LevelUpHistory not found: id=" + levelUpHistoryId));

    entity.setRewardStatus(status);
    entity.setRewardTxHash(status == RewardStatus.SUCCESS ? txHash : null);
  }

  @Override
  @Transactional(readOnly = true)
  public LevelUpHistoriesSlice loadLevelUpHistories(Long userId, int page, int size) {
    Slice<LevelUpHistoryEntity> slice =
        levelUpHistoryJpaRepository.findByUserIdOrderByCreatedAtDesc(
            userId, PageRequest.of(page, size));

    return LevelUpHistoriesSlice.builder()
        .histories(slice.getContent().stream().map(this::mapToDomain).toList())
        .hasNext(slice.hasNext())
        .build();
  }

  private LevelUpHistory mapToDomain(LevelUpHistoryEntity entity) {
    return LevelUpHistory.builder()
        .id(entity.getId())
        .userId(entity.getUserId())
        .levelPolicyId(entity.getLevelPolicyId())
        .fromLevel(entity.getFromLevel())
        .toLevel(entity.getToLevel())
        .spentXp(entity.getSpentXp())
        .rewardMztk(entity.getRewardMztk())
        .rewardStatus(entity.getRewardStatus())
        .rewardTxHash(entity.getRewardTxHash())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  private LevelUpHistoryEntity mapToEntity(LevelUpHistory history) {
    return LevelUpHistoryEntity.builder()
        .id(history.getId())
        .userId(history.getUserId())
        .levelPolicyId(history.getLevelPolicyId())
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
