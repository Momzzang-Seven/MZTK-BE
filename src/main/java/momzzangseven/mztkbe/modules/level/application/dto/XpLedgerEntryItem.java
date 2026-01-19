package momzzangseven.mztkbe.modules.level.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;

@Builder
public record XpLedgerEntryItem(
    Long xpLedgerId,
    XpType type,
    int xpAmount,
    LocalDate earnedOn,
    LocalDateTime occurredAt,
    String idempotencyKey,
    String sourceRef,
    LocalDateTime createdAt) {}
