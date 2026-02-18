package momzzangseven.mztkbe.modules.post.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.post.PostUnauthorizedException;

@Getter
public class Post {
  private final Long id;
  private final Long userId;
  private final PostType type;
  private String title;
  private String content;
  private List<String> imageUrls;
  private Long reward;
  private Boolean isSolved;

  private final LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  @Builder
  public Post(
      Long id,
      Long userId,
      PostType type,
      String title,
      String content,
      List<String> imageUrls,
      Long reward,
      Boolean isSolved,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this.id = id;
    this.userId = userId;
    this.type = type;
    this.title = title;
    this.content = content;
    this.imageUrls = imageUrls != null ? imageUrls : new ArrayList<>();
    this.reward = reward;
    this.isSolved = isSolved;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static Post create(
      Long userId,
      PostType type,
      String title,
      String content,
      Long reward,
      List<String> imageUrls) {

    if (userId == null) throw new IllegalArgumentException("작성자 ID는 필수입니다.");
    if (type == null) throw new IllegalArgumentException("게시글 타입은 필수입니다.");
    if (title == null || title.isBlank()) throw new IllegalArgumentException("제목을 입력해주세요.");
    if (content == null || content.isBlank()) throw new IllegalArgumentException("내용을 입력해주세요.");

    if (type == PostType.QUESTION) {
      if (reward == null || reward <= 0) {
        throw new IllegalArgumentException("질문 게시글은 보상(XP)이 필요합니다.");
      }
    } else if (type == PostType.FREE) {
      reward = 0L;
    }

    return Post.builder()
        .userId(userId)
        .type(type)
        .title(title)
        .content(content)
        .reward(reward)
        .imageUrls(imageUrls != null ? imageUrls : new ArrayList<>())
        .isSolved(false)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  public void validateOwnership(Long currentUserId) {
    if (!this.userId.equals(currentUserId)) {
      throw new PostUnauthorizedException();
    }
  }

  public void update(String title, String content, List<String> imageUrls) {
    boolean isUpdated = false;

    if (title != null) {
      if (title.isBlank()) throw new IllegalArgumentException("수정할 제목은 비워둘 수 없습니다.");
      this.title = title;
      isUpdated = true;
    }

    if (content != null) {
      if (content.isBlank()) throw new IllegalArgumentException("수정할 내용은 비워둘 수 없습니다.");
      this.content = content;
      isUpdated = true;
    }

    if (imageUrls != null) {
      this.imageUrls = imageUrls;
      isUpdated = true;
    }

    if (isUpdated) {
      this.updatedAt = LocalDateTime.now();
    }
  }
}
