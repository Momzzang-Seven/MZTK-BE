package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AnswerEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "post_id", nullable = false)
  private Long postId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  @Column(name = "is_accepted", nullable = false)
  private Boolean isAccepted = false;

  @CreatedDate
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  public AnswerEntity(Long id, Long postId, Long userId, String content, Boolean isAccepted) {
    this.id = id;
    this.postId = postId;
    this.userId = userId;
    this.content = content;
    this.isAccepted = isAccepted != null ? isAccepted : false;
  }
}
