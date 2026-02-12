package momzzangseven.mztkbe.modules.level.application.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import momzzangseven.mztkbe.global.error.level.RewardIntentCreationException;
import momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.LevelUpUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.LevelUpHistoryPort;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadActiveWalletPort;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkCommand;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkPort;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkResult;
import momzzangseven.mztkbe.modules.level.application.port.out.UserProgressPort;
import momzzangseven.mztkbe.modules.level.domain.model.LevelPolicy;
import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;
import momzzangseven.mztkbe.modules.level.domain.model.RewardStatus;
import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxPhase;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LevelUpService implements LevelUpUseCase {

  private final UserProgressPort userProgressPort;
  private final LevelPolicyResolver levelPolicyResolver;
  private final LevelUpHistoryPort levelUpHistoryPort;
  private final RewardMztkPort rewardMztkPort;
  private final LoadActiveWalletPort loadActiveWalletPort;

  @Value("${web3.explorer.base-url:}")
  private String explorerBaseUrl;

  @Override
  public LevelUpResult execute(LevelUpCommand command) {
    if (command == null) {
      throw new LevelUpCommandInvalidException("command is required");
    }
    command.validate();

    Long userId = command.userId();
    String activeWalletAddress =
        loadActiveWalletPort
            .loadActiveWalletAddress(userId)
            .orElseThrow(() -> new WalletNotConnectedException(userId));
    userProgressPort.loadOrCreateUserProgress(userId);

    UserProgress progress = userProgressPort.loadUserProgressWithLock(userId);
    LocalDateTime now = LocalDateTime.now();

    int fromLevel = progress.getLevel();
    LevelPolicy policy = levelPolicyResolver.resolveLevelUpPolicy(fromLevel, now);
    int requiredXp = policy.getRequiredXp();

    UserProgress updatedProgress = progress.levelUp(requiredXp, now);

    userProgressPort.saveUserProgress(updatedProgress);

    int toLevel = updatedProgress.getLevel();
    int rewardMztk = policy.getRewardMztk();

    LevelUpHistory savedHistory =
        levelUpHistoryPort.saveLevelUpHistory(
            LevelUpHistory.createPending(
                userId, policy.getId(), fromLevel, toLevel, requiredXp, rewardMztk));

    RewardMztkResult rewardResult =
        attemptReward(userId, rewardMztk, savedHistory.getId(), activeWalletAddress);
    RewardStatus rewardStatus = toLegacyRewardStatus(rewardResult.status());

    return LevelUpResult.builder()
        .levelUpHistoryId(savedHistory.getId())
        .fromLevel(fromLevel)
        .toLevel(toLevel)
        .spentXp(requiredXp)
        .rewardMztk(rewardMztk)
        .rewardStatus(rewardStatus)
        .rewardTxStatus(rewardResult.status())
        .rewardTxPhase(Web3TxPhase.from(rewardResult.status()))
        .rewardTxHash(rewardResult.txHash())
        .rewardExplorerUrl(buildExplorerUrl(rewardResult.txHash()))
        .build();
  }

  private RewardMztkResult attemptReward(
      Long userId, int rewardMztk, Long referenceId, String toWalletAddress) {
    if (rewardMztk <= 0) {
      return RewardMztkResult.builder().status(Web3TxStatus.SUCCEEDED).build();
    }

    try {
      RewardMztkResult result =
          rewardMztkPort.reward(
              RewardMztkCommand.builder()
                  .userId(userId)
                  .rewardMztk(rewardMztk)
                  .referenceId(referenceId)
                  .toWalletAddress(toWalletAddress)
                  .build());
      if (result == null) {
        return RewardMztkResult.created("NULL_RESULT");
      }
      result.validate();
      return result;
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new RewardIntentCreationException(userId, referenceId, e);
    }
  }

  private RewardStatus toLegacyRewardStatus(Web3TxStatus status) {
    if (status == Web3TxStatus.SUCCEEDED) {
      return RewardStatus.SUCCESS;
    }
    if (status == Web3TxStatus.FAILED_ONCHAIN) {
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
