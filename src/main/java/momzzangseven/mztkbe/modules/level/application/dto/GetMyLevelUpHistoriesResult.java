package momzzangseven.mztkbe.modules.level.application.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record GetMyLevelUpHistoriesResult(
    int page, int size, boolean hasNext, List<LevelUpHistoryItem> histories) {}
