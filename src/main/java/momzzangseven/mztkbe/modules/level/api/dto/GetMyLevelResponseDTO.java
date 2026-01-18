package momzzangseven.mztkbe.modules.level.api.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.GetMyLevelResult;

@Builder
public record GetMyLevelResponseDTO(
    int level, int availableXp, int requiredXpForNext, int rewardMztkForNext) {

  public static GetMyLevelResponseDTO from(GetMyLevelResult result) {
    return GetMyLevelResponseDTO.builder()
        .level(result.level())
        .availableXp(result.availableXp())
        .requiredXpForNext(result.requiredXpForNext())
        .rewardMztkForNext(result.rewardMztkForNext())
        .build();
  }
}
