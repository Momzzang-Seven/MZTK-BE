package momzzangseven.mztkbe.modules.post.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record PostListResult(
    Long postId,
    PostType type,
    String title,
    String content,
    Long userId,
    String nickname,
    String profileImageUrl,
    Long reward,
    boolean isSolved,
    List<String> tags,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static PostListResult fromDomain(Post post, String nickname, String profileImageUrl) {
    return new PostListResult(
        post.getId(),
        post.getType(),
        post.getTitle(),
        post.getContent(),
        post.getUserId(),
        nickname,
        profileImageUrl,
        post.getReward(),
        post.getIsSolved() != null ? post.getIsSolved() : false,
        post.getTags(),
        post.getCreatedAt(),
        post.getUpdatedAt());
  }
}
