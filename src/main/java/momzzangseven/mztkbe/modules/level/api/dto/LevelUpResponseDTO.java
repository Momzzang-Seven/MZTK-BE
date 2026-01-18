package momzzangseven.mztkbe.modules.level.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpResult;
import momzzangseven.mztkbe.modules.level.domain.model.RewardStatus;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LevelUpResponseDTO(
    Long levelUpHistoryId,
    int fromLevel,
    int toLevel,
    int spentXp,
    int rewardMztk,
    RewardStatus rewardStatus,
    String rewardTxHash) {

  public static LevelUpResponseDTO from(LevelUpResult result) {
    return LevelUpResponseDTO.builder()
        .levelUpHistoryId(result.levelUpHistoryId())
        .fromLevel(result.fromLevel())
        .toLevel(result.toLevel())
        .spentXp(result.spentXp())
        .rewardMztk(result.rewardMztk())
        .rewardStatus(result.rewardStatus())
        .rewardTxHash(result.rewardTxHash())
        .build();
  }
}