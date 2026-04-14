package momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.*;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
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
      PostStatus status) {
    this.id = id;
    this.userId = userId;
    this.type = type;
    this.title = title;
    this.content = content;
    this.reward = reward;
    this.acceptedAnswerId = acceptedAnswerId;
    this.status = Objects.requireNonNull(status, "status must not be null");
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
        .tags(tags)
        .createdAt(this.createdAt)
        .updatedAt(this.updatedAt)
        .build();
  }

  public Post toDomain() {
    return toDomain(new ArrayList<>());
  }
}
