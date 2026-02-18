package momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.level.domain.model.XpPolicy;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;

@Entity
@Table(
    name = "xp_policies",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"type", "effective_from"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class XpPolicyEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 50)
  private XpType type;

  @Column(name = "xp_amount", nullable = false)
  private int xpAmount;

  @Column(name = "daily_cap", nullable = false)
  private int dailyCap;

  @Column(name = "effective_from", nullable = false)
  private LocalDateTime effectiveFrom;

  @Column(name = "effective_to")
  private LocalDateTime effectiveTo;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  /** Domain model -> Entity */
  public static XpPolicyEntity from(XpPolicy domain) {
    return XpPolicyEntity.builder()
        .id(domain.getId())
        .type(domain.getType())
        .xpAmount(domain.getXpAmount())
        .dailyCap(domain.getDailyCap())
        .effectiveFrom(domain.getEffectiveFrom())
        .effectiveTo(domain.getEffectiveTo())
        .enabled(domain.isEnabled())
        .build();
  }

  /** Entity -> Domain model */
  public XpPolicy toDomain() {
    return XpPolicy.builder()
        .id(this.id)
        .type(this.type)
        .xpAmount(this.xpAmount)
        .dailyCap(this.dailyCap)
        .effectiveFrom(this.effectiveFrom)
        .effectiveTo(this.effectiveTo)
        .enabled(this.enabled)
        .build();
  }

  @PrePersist
  protected void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = LocalDateTime.now();
    }
  }
}
