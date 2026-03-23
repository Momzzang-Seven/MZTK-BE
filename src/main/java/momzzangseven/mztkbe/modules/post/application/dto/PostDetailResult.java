package momzzangseven.mztkbe.modules.post.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record PostDetailResult(
    Long postId,
    PostType type,
    String title,
    String content,
    Long userId,
    String nickname,
    String profileImageUrl,
    List<String> finalObjectKeys,
    Long reward,
    boolean isSolved,
    List<String> tags,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static PostDetailResult fromDomain(
      Post post, String nickname, String profileImageUrl, List<String> finalObjectKeys) {
    return new PostDetailResult(
        post.getId(),
        post.getType(),
        post.getTitle(),
        post.getContent(),
        post.getUserId(),
        nickname,
        profileImageUrl,
        finalObjectKeys,
        post.getReward(),
        post.getIsSolved() != null ? post.getIsSolved() : false,
        post.getTags(),
        post.getCreatedAt(),
        post.getUpdatedAt());
  }
}
