package momzzangseven.mztkbe.modules.post.domain.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class PostLike {

  private final Long id;
  private final PostLikeTargetType targetType;
  private final Long targetId;
  private final Long userId;
  private final LocalDateTime createdAt;

  private PostLike(
      Long id, PostLikeTargetType targetType, Long targetId, Long userId, LocalDateTime createdAt) {
    this.id = id;
    this.targetType = targetType;
    this.targetId = targetId;
    this.userId = userId;
    this.createdAt = createdAt;
  }

  public static PostLike create(PostLikeTargetType targetType, Long targetId, Long userId) {
    if (targetType == null) {
      throw new IllegalArgumentException("targetType is required.");
    }
    if (targetId == null || targetId <= 0) {
      throw new IllegalArgumentException("targetId must be positive.");
    }
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("userId must be positive.");
    }
    return PostLike.builder()
        .targetType(targetType)
        .targetId(targetId)
        .userId(userId)
        .createdAt(LocalDateTime.now())
        .build();
  }
}
