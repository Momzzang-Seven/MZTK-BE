package momzzangseven.mztkbe.modules.level.api.dto;

import java.util.List;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.MyLevelUpHistoriesResult;

@Builder
public record MyLevelUpHistoriesResponseDTO(
    int page, int size, boolean hasNext, List<LevelUpHistoryResponseDTO> histories) {

  public static MyLevelUpHistoriesResponseDTO from(MyLevelUpHistoriesResult result) {
    return MyLevelUpHistoriesResponseDTO.builder()
        .page(result.page())
        .size(result.size())
        .hasNext(result.hasNext())
        .histories(result.histories().stream().map(LevelUpHistoryResponseDTO::from).toList())
        .build();
  }
}
