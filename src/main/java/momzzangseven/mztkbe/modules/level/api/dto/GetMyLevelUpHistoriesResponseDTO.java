package momzzangseven.mztkbe.modules.level.api.dto;

import java.util.List;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.GetMyLevelUpHistoriesResult;

@Builder
public record GetMyLevelUpHistoriesResponseDTO(
    int page, int size, boolean hasNext, List<LevelUpHistoryResponseDTO> histories) {

  public static GetMyLevelUpHistoriesResponseDTO from(GetMyLevelUpHistoriesResult result) {
    return GetMyLevelUpHistoriesResponseDTO.builder()
        .page(result.page())
        .size(result.size())
        .hasNext(result.hasNext())
        .histories(result.histories().stream().map(LevelUpHistoryResponseDTO::from).toList())
        .build();
  }
}

