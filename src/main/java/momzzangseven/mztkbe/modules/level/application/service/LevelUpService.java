package momzzangseven.mztkbe.modules.level.application.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpResult;
import momzzangseven.mztkbe.modules.level.application.dto.RewardMztkCommand;
import momzzangseven.mztkbe.modules.level.application.dto.RewardMztkResult;
import momzzangseven.mztkbe.modules.level.application.port.in.LevelUpUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadUserProgressPort;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkPort;
import momzzangseven.mztkbe.modules.level.application.port.out.SaveLevelUpHistoryPort;
import momzzangseven.mztkbe.modules.level.application.port.out.SaveUserProgressPort;
import momzzangseven.mztkbe.modules.level.application.port.out.UpdateLevelUpHistoryRewardPort;
import momzzangseven.mztkbe.modules.level.domain.model.LevelPolicy;
import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;
import momzzangseven.mztkbe.modules.level.domain.model.RewardStatus;
import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LevelUpService implements LevelUpUseCase {

  private final LoadUserProgressPort loadUserProgressPort;
  private final SaveUserProgressPort saveUserProgressPort;
  private final LevelPolicyResolver levelPolicyResolver;
  private final SaveLevelUpHistoryPort saveLevelUpHistoryPort;
  private final UpdateLevelUpHistoryRewardPort updateLevelUpHistoryRewardPort;
  private final RewardMztkPort rewardMztkPort;

  @Override
  public LevelUpResult execute(LevelUpCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
    command.validate();

    Long userId = command.userId();
    loadUserProgressPort.loadOrCreateUserProgress(userId);

    UserProgress progress = loadUserProgressPort.loadUserProgressWithLock(userId);
    LocalDateTime now = LocalDateTime.now();

    int fromLevel = progress.getLevel();
    LevelPolicy policy = levelPolicyResolver.resolveLevelUpPolicy(fromLevel, now);
    int requiredXp = policy.getRequiredXp();

    UserProgress updatedProgress = progress.levelUp(requiredXp, now);

    saveUserProgressPort.saveUserProgress(updatedProgress);

    int toLevel = updatedProgress.getLevel();
    int rewardMztk = policy.getRewardMztk();

    LevelUpHistory savedHistory =
        saveLevelUpHistoryPort.saveLevelUpHistory(
            LevelUpHistory.createPending(
                userId, policy.getId(), fromLevel, toLevel, requiredXp, rewardMztk));

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
