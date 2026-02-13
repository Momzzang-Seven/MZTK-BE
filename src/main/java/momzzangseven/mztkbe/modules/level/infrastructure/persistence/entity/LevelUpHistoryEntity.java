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
import lombok.Setter;
import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;

@Entity
@Table(
    name = "level_up_histories",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "to_level"})})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LevelUpHistoryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "level_policy_id")
  private Long levelPolicyId;

  @Column(name = "from_level", nullable = false)
  private int fromLevel;

  @Column(name = "to_level", nullable = false)
  private int toLevel;

  @Column(name = "spent_xp", nullable = false)
  private int spentXp;

  @Column(name = "reward_mztk", nullable = false)
  private int rewardMztk;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  /** Domain model -> Entity */
  public static LevelUpHistoryEntity from(LevelUpHistory domain) {
    return LevelUpHistoryEntity.builder()
        .id(domain.getId())
        .userId(domain.getUserId())
        .levelPolicyId(domain.getLevelPolicyId())
        .fromLevel(domain.getFromLevel())
        .toLevel(domain.getToLevel())
        .spentXp(domain.getSpentXp())
        .rewardMztk(domain.getRewardMztk())
        .createdAt(domain.getCreatedAt())
        .build();
  }

  /** Entity -> Domain model */
  public LevelUpHistory toDomain() {
    return LevelUpHistory.reconstitute(
        this.id,
        this.userId,
        this.levelPolicyId,
        this.fromLevel,
        this.toLevel,
        this.spentXp,
        this.rewardMztk,
        this.createdAt);
  }

  @PrePersist
  protected void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = LocalDateTime.now();
    }
  }
}
