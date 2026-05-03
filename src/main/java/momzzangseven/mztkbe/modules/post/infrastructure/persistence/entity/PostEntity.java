package momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.*;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PostEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PostType type;

  @Column(length = 255)
  private String title;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  private Long reward;

  @Column(name = "accepted_answer_id")
  private Long acceptedAnswerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PostStatus status;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "publication_status",
      nullable = false,
      length = 20,
      columnDefinition = "varchar(20) default 'VISIBLE'")
  private PostPublicationStatus publicationStatus;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "moderation_status",
      nullable = false,
      length = 20,
      columnDefinition = "varchar(20) default 'NORMAL'")
  private PostModerationStatus moderationStatus;

  @Column(name = "current_create_execution_intent_id", length = 100)
  private String currentCreateExecutionIntentId;

  @Column(name = "publication_failure_terminal_status", length = 40)
  private String publicationFailureTerminalStatus;

  @Column(name = "publication_failure_reason", length = 500)
  private String publicationFailureReason;

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  public PostEntity(
      Long id,
      Long userId,
      PostType type,
      String title,
      String content,
      Long reward,
      Long acceptedAnswerId,
      PostStatus status,
      PostPublicationStatus publicationStatus,
      PostModerationStatus moderationStatus,
      String currentCreateExecutionIntentId,
      String publicationFailureTerminalStatus,
      String publicationFailureReason) {
    this.id = id;
    this.userId = userId;
    this.type = type;
    this.title = title;
    this.content = content;
    this.reward = reward;
    this.acceptedAnswerId = acceptedAnswerId;
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.publicationStatus =
        publicationStatus == null ? PostPublicationStatus.VISIBLE : publicationStatus;
    this.moderationStatus =
        moderationStatus == null ? PostModerationStatus.NORMAL : moderationStatus;
    this.currentCreateExecutionIntentId = currentCreateExecutionIntentId;
    this.publicationFailureTerminalStatus = publicationFailureTerminalStatus;
    this.publicationFailureReason = publicationFailureReason;
  }

  public static PostEntity fromDomain(Post post) {
    return PostEntity.builder()
        .id(post.getId())
        .userId(post.getUserId())
        .type(post.getType())
        .title(post.getTitle())
        .content(post.getContent())
        .reward(post.getReward())
        .acceptedAnswerId(post.getAcceptedAnswerId())
        .status(post.getStatus())
        .publicationStatus(post.getPublicationStatus())
        .moderationStatus(post.getModerationStatus())
        .currentCreateExecutionIntentId(post.getCurrentCreateExecutionIntentId())
        .publicationFailureTerminalStatus(post.getPublicationFailureTerminalStatus())
        .publicationFailureReason(post.getPublicationFailureReason())
        .build();
  }

  public Post toDomain(List<String> tags) {
    return Post.builder()
        .id(this.id)
        .userId(this.userId)
        .type(this.type)
        .title(this.title)
        .content(this.content)
        .reward(this.reward)
        .acceptedAnswerId(this.acceptedAnswerId)
        .status(this.status)
        .publicationStatus(this.publicationStatus)
        .moderationStatus(this.moderationStatus)
        .currentCreateExecutionIntentId(this.currentCreateExecutionIntentId)
        .publicationFailureTerminalStatus(this.publicationFailureTerminalStatus)
        .publicationFailureReason(this.publicationFailureReason)
        .tags(tags)
        .createdAt(this.createdAt)
        .updatedAt(this.updatedAt)
        .build();
  }

  public Post toDomain() {
    return toDomain(new ArrayList<>());
  }
}
