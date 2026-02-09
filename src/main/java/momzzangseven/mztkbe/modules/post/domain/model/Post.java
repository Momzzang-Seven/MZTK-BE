package momzzangseven.mztkbe.modules.post.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Post {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PostType type;

  @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "post_images", joinColumns = @JoinColumn(name = "post_id"))
  @Column(name = "image_url")
  private List<String> imageUrls = new ArrayList<>();

  @Column(nullable = true)
  private Long reward;

  @Column(nullable = true)
  private Boolean isSolved;

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate private LocalDateTime updatedAt;

  @Builder
  public Post(
      Long userId,
      PostType type,
      String title,
      String content,
      Long reward,
      List<String> imageUrls) {
    this.userId = userId;
    this.type = type;
    this.title = title;
    this.content = content;
    this.imageUrls = (imageUrls != null) ? imageUrls : new ArrayList<>();

    if (type == PostType.QUESTION) {
      this.reward = reward;
      this.isSolved = false;
    } else {
      this.reward = null;
      this.isSolved = null;
    }
  }

  // --- 비즈니스 로직 ---

  /** 게시글 수정 시 이미지도 함께 수정할 수 있도록 파라미터 추가 */
  public void update(String title, String content, List<String> imageUrls) {
    this.title = title;
    this.content = content;
    if (imageUrls != null) {
      this.imageUrls = imageUrls;
    }
  }

  public void markAsSolved() {
    if (this.type != PostType.QUESTION) {
      throw new IllegalStateException("자유게시글은 해결 상태를 가질 수 없습니다.");
    }
    this.isSolved = true;
  }

  public boolean isQuestion() {
    return this.type == PostType.QUESTION;
  }
}
