package momzzangseven.mztkbe.modules.post.api.dto;

import momzzangseven.mztkbe.modules.post.application.dto.PostLikeResult;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;

public record PostLikeResponse(
    PostLikeTargetType targetType, Long targetId, boolean liked, long likeCount) {

  public static PostLikeResponse from(PostLikeResult result) {
    return new PostLikeResponse(
        result.targetType(), result.targetId(), result.liked(), result.likeCount());
  }
}
