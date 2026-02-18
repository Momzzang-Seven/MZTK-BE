package momzzangseven.mztkbe.modules.level.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;

/** Domain model for XP ledger entries (earned XP history). */
@Getter
@Builder(toBuilder = true)
public class XpLedgerEntry {
  private Long id;
  private Long userId;
  private XpType type;
  private int xpAmount;
  private LocalDate earnedOn;
  private LocalDateTime occurredAt;
  private String idempotencyKey;
  private String sourceRef;
  private LocalDateTime createdAt;

  public static XpLedgerEntry create(
      Long userId,
      XpType type,
      int xpAmount,
      LocalDate earnedOn,
      LocalDateTime occurredAt,
      String idempotencyKey,
      String sourceRef) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (type == null) {
      throw new IllegalArgumentException("type is required");
    }
    if (xpAmount <= 0) {
      throw new IllegalArgumentException("xpAmount must be positive");
    }
    if (earnedOn == null) {
      throw new IllegalArgumentException("earnedOn is required");
    }
    if (occurredAt == null) {
      throw new IllegalArgumentException("occurredAt is required");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey is required");
    }

    return XpLedgerEntry.builder()
        .userId(userId)
        .type(type)
        .xpAmount(xpAmount)
        .earnedOn(earnedOn)
        .occurredAt(occurredAt)
        .idempotencyKey(idempotencyKey)
        .sourceRef(sourceRef)
        .createdAt(LocalDateTime.now())
        .build();
  }
}
