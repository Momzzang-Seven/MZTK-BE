package momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
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

  @ElementCollection
  @CollectionTable(name = "post_images", joinColumns = @JoinColumn(name = "post_id"))
  private List<String> imageUrls = new ArrayList<>();

  private Long reward;

  @Column(nullable = false)
  private Boolean isSolved = false;

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
      List<String> imageUrls,
      Long reward,
      Boolean isSolved) {
    this.id = id;
    this.userId = userId;
    this.type = type;
    this.title = title;
    this.content = content;
    this.imageUrls = imageUrls == null ? new ArrayList<>() : new ArrayList<>(imageUrls);
    this.reward = reward;
    this.isSolved = isSolved != null ? isSolved : false;
  }

  public static PostEntity fromDomain(Post post) {
    return PostEntity.builder()
        .id(post.getId())
        .userId(post.getUserId())
        .type(post.getType())
        .title(post.getTitle())
        .content(post.getContent())
        .imageUrls(post.getImageUrls())
        .reward(post.getReward())
        .isSolved(post.getIsSolved())
        .build();
  }

  public Post toDomain(List<String> tags) {
    return Post.builder()
        .id(this.id)
        .userId(this.userId)
        .type(this.type)
        .title(this.title)
        .content(this.content)
        .imageUrls(this.imageUrls == null ? new ArrayList<>() : new ArrayList<>(this.imageUrls))
        .reward(this.reward)
        .isSolved(this.isSolved)
        .tags(tags)
        .createdAt(this.createdAt)
        .updatedAt(this.updatedAt)
        .build();
  }

  public Post toDomain() {
    return toDomain(new ArrayList<>());
  }
}
