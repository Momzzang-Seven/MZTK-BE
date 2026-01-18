package momzzangseven.mztkbe.modules.level.application.dto;

import java.util.List;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;

@Builder
public record LevelUpHistoriesSlice(List<LevelUpHistory> histories, boolean hasNext) {}
