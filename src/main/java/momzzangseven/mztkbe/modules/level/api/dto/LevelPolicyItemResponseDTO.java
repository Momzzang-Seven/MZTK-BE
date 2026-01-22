package momzzangseven.mztkbe.modules.level.api.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.LevelPolicyItem;

@Builder
public record LevelPolicyItemResponseDTO(
    int currentLevel, int toLevel, int requiredXp, int rewardMztk) {

  public static LevelPolicyItemResponseDTO from(LevelPolicyItem item) {
    return LevelPolicyItemResponseDTO.builder()
        .currentLevel(item.currentLevel())
        .toLevel(item.toLevel())
        .requiredXp(item.requiredXp())
        .rewardMztk(item.rewardMztk())
        .build();
  }
}
