package momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;

@Entity
@Table(name = "user_progress")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserProgressEntity {

  @Id
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "level", nullable = false)
  private int level;

  @Column(name = "available_xp", nullable = false)
  private int availableXp;

  @Column(name = "lifetime_xp", nullable = false)
  private int lifetimeXp;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /** Domain model -> Entity */
  public static UserProgressEntity from(UserProgress domain) {
    return UserProgressEntity.builder()
        .userId(domain.getUserId())
        .level(domain.getLevel())
        .availableXp(domain.getAvailableXp())
        .lifetimeXp(domain.getLifetimeXp())
        .createdAt(domain.getCreatedAt())
        .updatedAt(domain.getUpdatedAt())
        .build();
  }

  /** Entity -> Domain model */
  public UserProgress toDomain() {
    return UserProgress.builder()
        .userId(this.userId)
        .level(this.level)
        .availableXp(this.availableXp)
        .lifetimeXp(this.lifetimeXp)
        .createdAt(this.createdAt)
        .updatedAt(this.updatedAt)
        .build();
  }

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (this.createdAt == null) {
      this.createdAt = now;
    }
    if (this.updatedAt == null) {
      this.updatedAt = now;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
