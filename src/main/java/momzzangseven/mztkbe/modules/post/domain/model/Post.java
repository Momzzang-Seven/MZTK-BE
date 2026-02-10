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
  private final LocalDateTime updatedAt;

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

  public void validateOwnership(Long currentUserId) {
    if (!this.userId.equals(currentUserId)) {
      throw new PostUnauthorizedException();
    }
  }

  public void update(String title, String content, List<String> imageUrls) {
    this.title = title;
    this.content = content;
    if (imageUrls != null) this.imageUrls = imageUrls;
  }
}
