package momzzangseven.mztkbe.modules.level.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpHistoryItem;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxPhase;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LevelUpHistoryResponseDTO(
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
    LocalDateTime createdAt) {

  public static LevelUpHistoryResponseDTO from(LevelUpHistoryItem item) {
    return LevelUpHistoryResponseDTO.builder()
        .levelUpHistoryId(item.levelUpHistoryId())
        .fromLevel(item.fromLevel())
        .toLevel(item.toLevel())
        .spentXp(item.spentXp())
        .rewardMztk(item.rewardMztk())
        .rewardStatus(item.rewardStatus())
        .rewardTxStatus(item.rewardTxStatus())
        .rewardTxPhase(item.rewardTxPhase())
        .rewardTxHash(item.rewardTxHash())
        .rewardExplorerUrl(item.rewardExplorerUrl())
        .createdAt(item.createdAt())
        .build();
  }
}
