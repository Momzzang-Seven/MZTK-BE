package momzzangseven.mztkbe.modules.level.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.XpLedgerEntryItem;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;

@Builder
public record XpLedgerEntryResponseDTO(
    Long xpLedgerId,
    XpType type,
    int xpAmount,
    LocalDate earnedOn,
    LocalDateTime occurredAt,
    String idempotencyKey,
    String sourceRef,
    LocalDateTime createdAt) {

  public static XpLedgerEntryResponseDTO from(XpLedgerEntryItem item) {
    return XpLedgerEntryResponseDTO.builder()
        .xpLedgerId(item.xpLedgerId())
        .type(item.type())
        .xpAmount(item.xpAmount())
        .earnedOn(item.earnedOn())
        .occurredAt(item.occurredAt())
        .idempotencyKey(item.idempotencyKey())
        .sourceRef(item.sourceRef())
        .createdAt(item.createdAt())
        .build();
  }
}
