package momzzangseven.mztkbe.modules.level.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpHistoriesSlice;
import momzzangseven.mztkbe.modules.level.application.dto.XpLedgerSlice;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadLevelPoliciesPort;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadLevelPolicyPort;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadLevelUpHistoriesPort;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadUserProgressPort;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadXpLedgerPort;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadXpPoliciesPort;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadXpPolicyPort;
import momzzangseven.mztkbe.modules.level.application.port.out.SaveLevelUpHistoryPort;
import momzzangseven.mztkbe.modules.level.application.port.out.SaveUserProgressPort;
import momzzangseven.mztkbe.modules.level.application.port.out.SaveXpLedgerPort;
import momzzangseven.mztkbe.modules.level.application.port.out.UpdateLevelUpHistoryRewardPort;
import momzzangseven.mztkbe.modules.level.domain.model.LevelPolicy;
import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;
import momzzangseven.mztkbe.modules.level.domain.model.RewardStatus;
import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;
import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;
import momzzangseven.mztkbe.modules.level.domain.model.XpPolicy;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.LevelPolicyEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.LevelUpHistoryEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.UserProgressEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpLedgerEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpPolicyEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.repository.LevelPolicyJpaRepository;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.repository.LevelUpHistoryJpaRepository;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.repository.UserProgressJpaRepository;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.repository.XpLedgerJpaRepository;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.repository.XpPolicyJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LevelPersistenceAdapter
    implements LoadUserProgressPort,
        SaveUserProgressPort,
        LoadLevelPolicyPort,
        LoadLevelPoliciesPort,
        LoadXpPolicyPort,
        LoadXpPoliciesPort,
        LoadXpLedgerPort,
        SaveXpLedgerPort,
        SaveLevelUpHistoryPort,
        UpdateLevelUpHistoryRewardPort,
        LoadLevelUpHistoriesPort {

  private final UserProgressJpaRepository userProgressJpaRepository;
  private final LevelPolicyJpaRepository levelPolicyJpaRepository;
  private final XpPolicyJpaRepository xpPolicyJpaRepository;
  private final XpLedgerJpaRepository xpLedgerJpaRepository;
  private final LevelUpHistoryJpaRepository levelUpHistoryJpaRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<UserProgress> loadUserProgress(Long userId) {
    return userProgressJpaRepository.findById(userId).map(this::mapToDomain);
  }

  @Override
  @Transactional
  public UserProgress loadUserProgressWithLock(Long userId) {
    return userProgressJpaRepository
        .findByUserIdForUpdate(userId)
        .map(this::mapToDomain)
        .orElseThrow(() -> new IllegalStateException("UserProgress not found: userId=" + userId));
  }

  @Override
  @Transactional
  public UserProgress loadOrCreateUserProgress(Long userId) {
    Optional<UserProgressEntity> existing = userProgressJpaRepository.findById(userId);
    if (existing.isPresent()) {
      return mapToDomain(existing.get());
    }

    // Race-safe lazy init: if another request creates it concurrently, re-read after conflict.
    try {
      UserProgressEntity created =
          userProgressJpaRepository.saveAndFlush(mapToEntity(UserProgress.createInitial(userId)));
      return mapToDomain(created);
    } catch (DataIntegrityViolationException e) {
      log.info("UserProgress already created concurrently: userId={}", userId);
      return userProgressJpaRepository.findById(userId).map(this::mapToDomain).orElseThrow(() -> e);
    }
  }

  @Override
  @Transactional
  public UserProgress saveUserProgress(UserProgress progress) {
    UserProgressEntity entity =
        userProgressJpaRepository
            .findById(progress.getUserId())
            .orElseGet(
                () ->
                    UserProgressEntity.builder()
                        .userId(progress.getUserId())
                        .level(progress.getLevel())
                        .availableXp(progress.getAvailableXp())
                        .lifetimeXp(progress.getLifetimeXp())
                        .createdAt(progress.getCreatedAt())
                        .updatedAt(progress.getUpdatedAt())
                        .build());

    entity.setLevel(progress.getLevel());
    entity.setAvailableXp(progress.getAvailableXp());
    entity.setLifetimeXp(progress.getLifetimeXp());
    entity.setUpdatedAt(progress.getUpdatedAt());

    return mapToDomain(userProgressJpaRepository.save(entity));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<LevelPolicy> loadLevelPolicy(int currentLevel, LocalDateTime at) {
    return levelPolicyJpaRepository
        .findActiveByLevel(currentLevel, at, PageRequest.of(0, 1))
        .stream()
        .findFirst()
        .map(this::mapToDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<LevelPolicy> loadLevelPolicies(LocalDateTime at) {
    Map<Integer, LevelPolicyEntity> latestByLevel =
        levelPolicyJpaRepository.findActivePolicies(at).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    LevelPolicyEntity::getLevel,
                    Function.identity(),
                    (a, b) -> a.getEffectiveFrom().isAfter(b.getEffectiveFrom()) ? a : b));

    return latestByLevel.values().stream()
        .sorted(Comparator.comparingInt(LevelPolicyEntity::getLevel))
        .map(this::mapToDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<XpPolicy> loadXpPolicies(LocalDateTime at) {
    Map<momzzangseven.mztkbe.modules.level.domain.model.XpType, XpPolicyEntity> latestByType =
        xpPolicyJpaRepository.findActivePolicies(at).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    XpPolicyEntity::getType,
                    Function.identity(),
                    (a, b) -> a.getEffectiveFrom().isAfter(b.getEffectiveFrom()) ? a : b));

    return latestByType.values().stream()
        .sorted(Comparator.comparing(XpPolicyEntity::getType))
        .map(this::mapToDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<XpPolicy> loadXpPolicy(XpType type, LocalDateTime at) {
    return xpPolicyJpaRepository.findActiveByType(type, at, PageRequest.of(0, 1)).stream()
        .findFirst()
        .map(this::mapToDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByUserIdAndIdempotencyKey(Long userId, String idempotencyKey) {
    return xpLedgerJpaRepository.existsByUserIdAndIdempotencyKey(userId, idempotencyKey);
  }

  @Override
  @Transactional(readOnly = true)
  public int countByUserIdAndTypeAndEarnedOn(
      Long userId, XpType type, java.time.LocalDate earnedOn) {
    return xpLedgerJpaRepository.countByUserIdAndTypeAndEarnedOn(userId, type, earnedOn);
  }

  @Override
  @Transactional(readOnly = true)
  public XpLedgerSlice loadXpLedgerEntries(Long userId, int page, int size) {
    Slice<XpLedgerEntity> slice =
        xpLedgerJpaRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    return XpLedgerSlice.builder()
        .entries(slice.getContent().stream().map(this::mapToDomain).toList())
        .hasNext(slice.hasNext())
        .build();
  }

  @Override
  @Transactional
  public XpLedgerEntry saveXpLedger(XpLedgerEntry entry) {
    XpLedgerEntity saved = xpLedgerJpaRepository.saveAndFlush(mapToEntity(entry));
    return mapToDomain(saved);
  }

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

  private UserProgress mapToDomain(UserProgressEntity entity) {
    return UserProgress.builder()
        .userId(entity.getUserId())
        .level(entity.getLevel())
        .availableXp(entity.getAvailableXp())
        .lifetimeXp(entity.getLifetimeXp())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  private UserProgressEntity mapToEntity(UserProgress progress) {
    return UserProgressEntity.builder()
        .userId(progress.getUserId())
        .level(progress.getLevel())
        .availableXp(progress.getAvailableXp())
        .lifetimeXp(progress.getLifetimeXp())
        .createdAt(progress.getCreatedAt())
        .updatedAt(progress.getUpdatedAt())
        .build();
  }

  private LevelPolicy mapToDomain(LevelPolicyEntity entity) {
    return LevelPolicy.builder()
        .id(entity.getId())
        .level(entity.getLevel())
        .requiredXp(entity.getRequiredXp())
        .rewardMztk(entity.getRewardMztk())
        .effectiveFrom(entity.getEffectiveFrom())
        .effectiveTo(entity.getEffectiveTo())
        .enabled(entity.isEnabled())
        .build();
  }

  private LevelPolicyEntity mapToEntity(LevelPolicy policy) {
    return LevelPolicyEntity.builder()
        .id(policy.getId())
        .level(policy.getLevel())
        .requiredXp(policy.getRequiredXp())
        .rewardMztk(policy.getRewardMztk())
        .effectiveFrom(policy.getEffectiveFrom())
        .effectiveTo(policy.getEffectiveTo())
        .enabled(policy.isEnabled())
        .createdAt(null)
        .build();
  }

  private XpPolicy mapToDomain(XpPolicyEntity entity) {
    return XpPolicy.builder()
        .id(entity.getId())
        .type(entity.getType())
        .xpAmount(entity.getXpAmount())
        .dailyCap(entity.getDailyCap())
        .effectiveFrom(entity.getEffectiveFrom())
        .effectiveTo(entity.getEffectiveTo())
        .enabled(entity.isEnabled())
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

  private XpLedgerEntry mapToDomain(XpLedgerEntity entity) {
    return XpLedgerEntry.builder()
        .id(entity.getId())
        .userId(entity.getUserId())
        .type(entity.getType())
        .xpAmount(entity.getXpAmount())
        .earnedOn(entity.getEarnedOn())
        .occurredAt(entity.getOccurredAt())
        .idempotencyKey(entity.getIdempotencyKey())
        .sourceRef(entity.getSourceRef())
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

  private XpLedgerEntity mapToEntity(XpLedgerEntry entry) {
    return XpLedgerEntity.builder()
        .id(entry.getId())
        .userId(entry.getUserId())
        .type(entry.getType())
        .xpAmount(entry.getXpAmount())
        .earnedOn(entry.getEarnedOn())
        .occurredAt(entry.getOccurredAt())
        .idempotencyKey(entry.getIdempotencyKey())
        .sourceRef(entry.getSourceRef())
        .createdAt(entry.getCreatedAt())
        .build();
  }
}
