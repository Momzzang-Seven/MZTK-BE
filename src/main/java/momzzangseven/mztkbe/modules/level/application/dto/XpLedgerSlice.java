package momzzangseven.mztkbe.modules.level.application.dto;

import java.util.List;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;

@Builder
public record XpLedgerSlice(List<XpLedgerEntry> entries, boolean hasNext) {}
