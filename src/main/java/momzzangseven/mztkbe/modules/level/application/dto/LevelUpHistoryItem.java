package momzzangseven.mztkbe.modules.level.application.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.domain.model.RewardStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxPhase;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

@Builder
public record LevelUpHistoryItem(
    Long levelUpHistoryId,
    int fromLevel,
    int toLevel,
    int spentXp,
    int rewardMztk,
    RewardStatus rewardStatus,
    Web3TxStatus rewardTxStatus,
    Web3TxPhase rewardTxPhase,
    String rewardTxHash,
    String rewardExplorerUrl,
    LocalDateTime createdAt) {}
