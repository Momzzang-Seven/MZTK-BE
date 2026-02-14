package momzzangseven.mztkbe.modules.level.application.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;

@Builder
public record XpDailyCapStatusItem(
    XpType type, int dailyCap, int grantedCount, int remainingCount) {}
