package momzzangseven.mztkbe.modules.level.application.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.error.level.NotEnoughXpException;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpResult;
import momzzangseven.mztkbe.modules.level.application.dto.RewardMztkCommand;
import momzzangseven.mztkbe.modules.level.application.dto.RewardMztkResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.LevelUpUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadUserProgressPort;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadXpLedgerPort;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadXpPolicyPort;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkPort;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LevelCommandService implements LevelUpUseCase, GrantXpUseCase {

  private final LoadUserProgressPort loadUserProgressPort;
  private final SaveUserProgressPort saveUserProgressPort;
  private final LevelPolicyResolver levelPolicyResolver;
  private final SaveLevelUpHistoryPort saveLevelUpHistoryPort;
  private final UpdateLevelUpHistoryRewardPort updateLevelUpHistoryRewardPort;
  private final RewardMztkPort rewardMztkPort;
  private final LoadXpPolicyPort loadXpPolicyPort;
  private final LoadXpLedgerPort loadXpLedgerPort;
  private final SaveXpLedgerPort saveXpLedgerPort;

  @Override
  public LevelUpResult execute(LevelUpCommand command) {
    if (command == null || command.userId() == null) {
      throw new UserNotAuthenticatedException();
    }

    Long userId = command.userId();
    loadUserProgressPort.loadOrCreateUserProgress(userId);

    UserProgress progress = loadUserProgressPort.loadUserProgressWithLock(userId);
    LocalDateTime now = LocalDateTime.now();

    LevelPolicy policy = levelPolicyResolver.resolveForLevelUp(progress.getLevel(), now);

    int requiredXp = policy.getRequiredXp();
    if (progress.getAvailableXp() < requiredXp) {
      throw new NotEnoughXpException(
          "Not enough XP to level up: availableXp="
              + progress.getAvailableXp()
              + ", requiredXp="
              + requiredXp);
    }

    int fromLevel = progress.getLevel();
    int toLevel = fromLevel + 1;
    int rewardMztk = policy.getRewardMztk();

    UserProgress updated =
        progress.toBuilder()
            .level(toLevel)
            .availableXp(progress.getAvailableXp() - requiredXp)
            .updatedAt(LocalDateTime.now())
            .build();

    saveUserProgressPort.saveUserProgress(updated);

    LevelUpHistory savedHistory;
    try {
      savedHistory =
          saveLevelUpHistoryPort.saveLevelUpHistory(
              LevelUpHistory.createPending(
                  userId, policy.getId(), fromLevel, toLevel, requiredXp, rewardMztk));
    } catch (DataIntegrityViolationException e) {
      throw new NotEnoughXpException("Level up already processed");
    }

    RewardMztkResult rewardResult = attemptReward(userId, rewardMztk, savedHistory.getId());
    updateLevelUpHistoryRewardPort.updateReward(
        savedHistory.getId(), rewardResult.status(), rewardResult.txHash());

    return LevelUpResult.builder()
        .levelUpHistoryId(savedHistory.getId())
        .fromLevel(fromLevel)
        .toLevel(toLevel)
        .spentXp(requiredXp)
        .rewardMztk(rewardMztk)
        .rewardStatus(rewardResult.status())
        .rewardTxHash(rewardResult.txHash())
        .build();
  }

  @Override
  public GrantXpResult execute(GrantXpCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
    command.validate();

    Long userId = command.userId();
    LocalDateTime occurredAt = command.occurredAt();
    XpType xpType = command.xpType();
    if (xpType == XpType.CHECK_IN || xpType == XpType.STREAK_7D) {
      throw new IllegalArgumentException("xpType is not supported yet: " + xpType);
    }

    loadUserProgressPort.loadOrCreateUserProgress(userId);
    UserProgress progress = loadUserProgressPort.loadUserProgressWithLock(userId);

    XpPolicy policy =
        loadXpPolicyPort
            .loadXpPolicy(xpType, occurredAt)
            .orElseThrow(() -> new IllegalStateException("XP policy not found: type=" + xpType));

    String idempotencyKey = command.idempotencyKey();
    if (loadXpLedgerPort.existsByUserIdAndIdempotencyKey(userId, idempotencyKey)) {
      return GrantXpResult.alreadyGranted(policy.getDailyCap());
    }

    java.time.LocalDate earnedOn = occurredAt.toLocalDate();
    int dailyCap = policy.getDailyCap();
    if (dailyCap > 0) {
      int grantedToday = loadXpLedgerPort.countByUserIdAndTypeAndEarnedOn(userId, xpType, earnedOn);
      if (grantedToday >= dailyCap) {
        return GrantXpResult.dailyCapReached(dailyCap);
      }
    }

    XpLedgerEntry entry =
        XpLedgerEntry.create(
            userId,
            xpType,
            policy.getXpAmount(),
            earnedOn,
            occurredAt,
            idempotencyKey,
            command.sourceRef());
    saveXpLedgerPort.saveXpLedger(entry);

    UserProgress updated =
        progress.toBuilder()
            .availableXp(progress.getAvailableXp() + policy.getXpAmount())
            .lifetimeXp(progress.getLifetimeXp() + policy.getXpAmount())
            .updatedAt(LocalDateTime.now())
            .build();
    updated = saveUserProgressPort.saveUserProgress(updated);

    return GrantXpResult.granted(policy.getXpAmount(), dailyCap);
  }

  private RewardMztkResult attemptReward(Long userId, int rewardMztk, Long referenceId) {
    if (rewardMztk <= 0) {
      return RewardMztkResult.builder().status(RewardStatus.SUCCESS).build();
    }

    try {
      RewardMztkResult result =
          rewardMztkPort.reward(
              RewardMztkCommand.builder()
                  .userId(userId)
                  .rewardMztk(rewardMztk)
                  .referenceId(referenceId)
                  .build());
      if (result == null) {
        return RewardMztkResult.failed("NULL_RESULT");
      }
      result.validate();
      return result;
    } catch (Exception e) {
      log.warn("RewardMztkPort failed: userId={}, referenceId={}", userId, referenceId, e);
      return RewardMztkResult.failed(e.getClass().getSimpleName());
    }
  }
}
