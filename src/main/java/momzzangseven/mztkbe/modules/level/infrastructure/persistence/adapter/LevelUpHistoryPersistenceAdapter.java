package momzzangseven.mztkbe.modules.level.infrastructure.persistence.adapter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.level.LevelUpAlreadyProcessedException;
import momzzangseven.mztkbe.modules.level.application.port.out.LevelUpHistoryPort;
import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;
import momzzangseven.mztkbe.modules.level.domain.model.RewardStatus;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.LevelUpHistoryEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.repository.LevelUpHistoryJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LevelUpHistoryPersistenceAdapter implements LevelUpHistoryPort {

  private final LevelUpHistoryJpaRepository levelUpHistoryJpaRepository;

  @Override
  @Transactional
  public LevelUpHistory saveLevelUpHistory(LevelUpHistory history) {
    try {
      LevelUpHistoryEntity saved = levelUpHistoryJpaRepository.saveAndFlush(mapToEntity(history));
      return mapToDomain(saved);
    } catch (DataIntegrityViolationException e) {
      throw new LevelUpAlreadyProcessedException();
    }
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
  public List<LevelUpHistory> loadLevelUpHistories(Long userId, int page, int size) {
    int fetchSize = Math.max(1, size + 1);
    return levelUpHistoryJpaRepository
        .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, fetchSize))
        .getContent()
        .stream()
        .map(this::mapToDomain)
        .toList();
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
