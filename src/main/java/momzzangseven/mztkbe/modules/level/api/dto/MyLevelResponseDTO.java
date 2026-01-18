package momzzangseven.mztkbe.modules.level.api.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.MyLevelResult;

@Builder
public record MyLevelResponseDTO(
    int level, int availableXp, int requiredXpForNext, int rewardMztkForNext) {

  public static MyLevelResponseDTO from(MyLevelResult result) {
    return MyLevelResponseDTO.builder()
        .level(result.level())
        .availableXp(result.availableXp())
        .requiredXpForNext(result.requiredXpForNext())
        .rewardMztkForNext(result.rewardMztkForNext())
        .build();
  }
}
