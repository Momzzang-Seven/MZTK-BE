package momzzangseven.mztkbe.modules.level.application.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.domain.model.RewardStatus;

@Builder
public record LevelUpHistoryItem(
    Long levelUpHistoryId,
    int fromLevel,
    int toLevel,
    int spentXp,
    int rewardMztk,
    RewardStatus rewardStatus,
    String rewardTxHash,
    LocalDateTime createdAt) {}
