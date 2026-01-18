package momzzangseven.mztkbe.modules.level.application.port.out.dto;

import java.util.List;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;

@Builder
public record LevelUpHistorySlice(List<LevelUpHistory> histories, boolean hasNext) {}
