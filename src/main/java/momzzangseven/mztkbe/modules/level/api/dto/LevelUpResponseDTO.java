package momzzangseven.mztkbe.modules.level.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpResult;
import momzzangseven.mztkbe.modules.level.domain.model.RewardStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxPhase;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LevelUpResponseDTO(
    Long levelUpHistoryId,
    int fromLevel,
    int toLevel,
    int spentXp,
    int rewardMztk,
    RewardStatus rewardStatus,
    Web3TxStatus rewardTxStatus,
    Web3TxPhase rewardTxPhase,
    String rewardTxHash,
    String rewardExplorerUrl) {

  public static LevelUpResponseDTO from(LevelUpResult result) {
    return LevelUpResponseDTO.builder()
        .levelUpHistoryId(result.levelUpHistoryId())
        .fromLevel(result.fromLevel())
        .toLevel(result.toLevel())
        .spentXp(result.spentXp())
        .rewardMztk(result.rewardMztk())
        .rewardStatus(result.rewardStatus())
        .rewardTxStatus(result.rewardTxStatus())
        .rewardTxPhase(result.rewardTxPhase())
        .rewardTxHash(result.rewardTxHash())
        .rewardExplorerUrl(result.rewardExplorerUrl())
        .build();
  }
}
