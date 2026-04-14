package momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "post_like",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_post_like_target_user",
          columnNames = {"target_type", "target_id", "user_id"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PostLikeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 20)
  private PostLikeTargetType targetType;

  @Column(name = "target_id", nullable = false)
  private Long targetId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private PostLikeEntity(Long id, PostLikeTargetType targetType, Long targetId, Long userId) {
    this.id = id;
    this.targetType = targetType;
    this.targetId = targetId;
    this.userId = userId;
  }
}
