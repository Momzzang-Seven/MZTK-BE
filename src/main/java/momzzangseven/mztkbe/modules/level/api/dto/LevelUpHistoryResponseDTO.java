package momzzangseven.mztkbe.modules.level.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpHistoryItem;
import momzzangseven.mztkbe.modules.level.domain.model.RewardStatus;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LevelUpHistoryResponseDTO(
    Long levelUpHistoryId,
    int fromLevel,
    int toLevel,
    int spentXp,
    int rewardMztk,
    RewardStatus rewardStatus,
    String rewardTxHash,
    LocalDateTime createdAt) {

  public static LevelUpHistoryResponseDTO from(LevelUpHistoryItem item) {
    return LevelUpHistoryResponseDTO.builder()
        .levelUpHistoryId(item.levelUpHistoryId())
        .fromLevel(item.fromLevel())
        .toLevel(item.toLevel())
        .spentXp(item.spentXp())
        .rewardMztk(item.rewardMztk())
        .rewardStatus(item.rewardStatus())
        .rewardTxHash(item.rewardTxHash())
        .createdAt(item.createdAt())
        .build();
  }
}