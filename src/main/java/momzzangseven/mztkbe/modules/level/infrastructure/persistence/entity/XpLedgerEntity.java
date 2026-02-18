package momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;

@Entity
@Table(
    name = "xp_ledger",
    indexes = {@Index(columnList = "user_id,type,earned_on")},
    uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "idempotency_key"})})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class XpLedgerEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 20)
  private XpType type;

  @Column(name = "xp_amount", nullable = false)
  private int xpAmount;

  @Column(name = "earned_on", nullable = false)
  private LocalDate earnedOn;

  @Column(name = "occurred_at", nullable = false)
  private LocalDateTime occurredAt;

  @Column(name = "idempotency_key", nullable = false, length = 200)
  private String idempotencyKey;

  @Column(name = "source_ref", length = 255)
  private String sourceRef;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  /** Domain model -> Entity */
  public static XpLedgerEntity from(XpLedgerEntry domain) {
    return XpLedgerEntity.builder()
        .id(domain.getId())
        .userId(domain.getUserId())
        .type(domain.getType())
        .xpAmount(domain.getXpAmount())
        .earnedOn(domain.getEarnedOn())
        .occurredAt(domain.getOccurredAt())
        .idempotencyKey(domain.getIdempotencyKey())
        .sourceRef(domain.getSourceRef())
        .createdAt(domain.getCreatedAt())
        .build();
  }

  /** Entity -> Domain model */
  public XpLedgerEntry toDomain() {
    return XpLedgerEntry.builder()
        .id(this.id)
        .userId(this.userId)
        .type(this.type)
        .xpAmount(this.xpAmount)
        .earnedOn(this.earnedOn)
        .occurredAt(this.occurredAt)
        .idempotencyKey(this.idempotencyKey)
        .sourceRef(this.sourceRef)
        .createdAt(this.createdAt)
        .build();
  }

  @PrePersist
  protected void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = LocalDateTime.now();
    }
  }
}
