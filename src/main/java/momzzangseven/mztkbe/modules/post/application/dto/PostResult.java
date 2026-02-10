package momzzangseven.mztkbe.modules.post.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record PostResult(
    Long postId,
    PostType type,
    String title,
    String content,
    Long userId,
    List<String> imageUrls,
    Long reward,
    boolean isSolved,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static PostResult fromDomain(Post post) {
    return new PostResult(
        post.getId(),
        post.getType(),
        post.getTitle(),
        post.getContent(),
        post.getUserId(),
        post.getImageUrls(),
        post.getReward(),
        post.getIsSolved() != null ? post.getIsSolved() : false,
        post.getCreatedAt(),
        post.getUpdatedAt());
  }
}
