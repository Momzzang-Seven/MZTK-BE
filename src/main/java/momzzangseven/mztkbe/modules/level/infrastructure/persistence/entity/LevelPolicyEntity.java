package momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import momzzangseven.mztkbe.modules.level.domain.model.LevelPolicy;

@Entity
@Table(
    name = "level_policies",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"level", "effective_from"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LevelPolicyEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "level", nullable = false)
  private int level;

  @Column(name = "required_xp", nullable = false)
  private int requiredXp;

  @Column(name = "reward_mztk", nullable = false)
  private int rewardMztk;

  @Column(name = "effective_from", nullable = false)
  private LocalDateTime effectiveFrom;

  @Column(name = "effective_to")
  private LocalDateTime effectiveTo;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  /** Domain model -> Entity */
  public static LevelPolicyEntity from(LevelPolicy domain) {
    return LevelPolicyEntity.builder()
        .id(domain.getId())
        .level(domain.getLevel())
        .requiredXp(domain.getRequiredXp())
        .rewardMztk(domain.getRewardMztk())
        .effectiveFrom(domain.getEffectiveFrom())
        .effectiveTo(domain.getEffectiveTo())
        .enabled(domain.isEnabled())
        .build();
  }

  /** Entity -> Domain model */
  public LevelPolicy toDomain() {
    return LevelPolicy.builder()
        .id(this.id)
        .level(this.level)
        .requiredXp(this.requiredXp)
        .rewardMztk(this.rewardMztk)
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
