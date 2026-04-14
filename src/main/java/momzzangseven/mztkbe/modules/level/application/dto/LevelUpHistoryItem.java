package momzzangseven.mztkbe.modules.level.application.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardStatus;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxPhase;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;

@Builder
public record LevelUpHistoryItem(
    Long levelUpHistoryId,
    int fromLevel,
    int toLevel,
    int spentXp,
    int rewardMztk,
    RewardStatus rewardStatus,
    RewardTxStatus rewardTxStatus,
    RewardTxPhase rewardTxPhase,
    String rewardTxHash,
    String rewardExplorerUrl,
    LocalDateTime createdAt) {}
