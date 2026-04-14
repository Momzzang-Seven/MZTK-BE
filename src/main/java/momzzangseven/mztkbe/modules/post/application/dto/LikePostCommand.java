package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;

public record LikePostCommand(
    Long postId, Long targetId, Long userId, PostLikeTargetType targetType) {

  public static LikePostCommand forPost(Long postId, Long userId) {
    return new LikePostCommand(postId, postId, userId, PostLikeTargetType.POST);
  }

  public static LikePostCommand forAnswer(Long postId, Long answerId, Long userId) {
    return new LikePostCommand(postId, answerId, userId, PostLikeTargetType.ANSWER);
  }

  public void validate() {
    if (postId == null || postId <= 0) {
      throw new IllegalArgumentException("postId must be positive.");
    }
    if (targetId == null || targetId <= 0) {
      throw new IllegalArgumentException("targetId must be positive.");
    }
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("userId must be positive.");
    }
    if (targetType == null) {
      throw new IllegalArgumentException("targetType is required.");
    }
  }
}
