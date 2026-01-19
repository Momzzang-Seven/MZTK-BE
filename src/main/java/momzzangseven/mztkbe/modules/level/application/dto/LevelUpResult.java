package momzzangseven.mztkbe.modules.level.application.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.level.domain.model.RewardStatus;

@Builder
public record LevelUpResult(
    Long levelUpHistoryId,
    int fromLevel,
    int toLevel,
    int spentXp,
    int rewardMztk,
    RewardStatus rewardStatus,
    String rewardTxHash) {}
