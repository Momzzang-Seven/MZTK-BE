package momzzangseven.mztkbe.modules.level.application.port.out.dto;

import java.util.List;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;

@Builder
public record XpLedgerEntrySlice(List<XpLedgerEntry> entries, boolean hasNext) {}
