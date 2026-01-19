package momzzangseven.mztkbe.modules.level.application.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

@Builder
public record GetMyXpLedgerResult(
    int page,
    int size,
    boolean hasNext,
    LocalDate earnedOn,
    List<XpLedgerEntryItem> entries,
    List<XpDailyCapStatusItem> todayCaps) {}
