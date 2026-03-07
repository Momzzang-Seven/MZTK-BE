package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
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

  @ElementCollection
  @CollectionTable(name = "answer_images", joinColumns = @JoinColumn(name = "answer_id"))
  @Column(name = "image_url")
  private List<String> imageUrls = new ArrayList<>();

  @CreatedDate
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  public AnswerEntity(
      Long id,
      Long postId,
      Long userId,
      String content,
      Boolean isAccepted,
      List<String> imageUrls) {
    this.id = id;
    this.postId = postId;
    this.userId = userId;
    this.content = content;
    this.isAccepted = isAccepted != null ? isAccepted : false;
    this.imageUrls = imageUrls == null ? new ArrayList<>() : new ArrayList<>(imageUrls);
  }

  public static AnswerEntity fromDomain(Answer answer) {
    return AnswerEntity.builder()
        .id(answer.getId())
        .postId(answer.getPostId())
        .userId(answer.getUserId())
        .content(answer.getContent())
        .isAccepted(answer.getIsAccepted())
        .imageUrls(answer.getImageUrls())
        .build();
  }

  public Answer toDomain() {
    return Answer.builder()
        .id(this.id)
        .postId(this.postId)
        .userId(this.userId)
        .content(this.content)
        .isAccepted(this.isAccepted)
        .createdAt(this.createdAt)
        .updatedAt(this.updatedAt)
        .imageUrls(this.imageUrls == null ? new ArrayList<>() : new ArrayList<>(this.imageUrls))
        .build();
  }
}
