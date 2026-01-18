package momzzangseven.mztkbe.modules.level.api.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.LevelPolicyItem;

@Builder
public record LevelPolicyResponseDTO(
    int currentLevel, int toLevel, int requiredXp, int rewardMztk) {

  public static LevelPolicyResponseDTO from(LevelPolicyItem item) {
    return LevelPolicyResponseDTO.builder()
        .currentLevel(item.currentLevel())
        .toLevel(item.toLevel())
        .requiredXp(item.requiredXp())
        .rewardMztk(item.rewardMztk())
        .build();
  }
}