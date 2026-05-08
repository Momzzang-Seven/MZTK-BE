package momzzangseven.mztkbe.modules.comment.application.dto;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.comment.domain.model.CommentTargetType;

public record CreateCommentCommand(
    CommentTargetType targetType,
    Long postId,
    Long answerId,
    Long userId,
    Long parentId,
    String content) {

  public CreateCommentCommand(Long postId, Long userId, Long parentId, String content) {
    this(CommentTargetType.POST, postId, null, userId, parentId, content);
  }

  public CreateCommentCommand {
    targetType = targetType == null ? CommentTargetType.POST : targetType;
    validate(targetType, postId, answerId, userId, content);
  }

  public static CreateCommentCommand forPost(
      Long postId, Long userId, Long parentId, String content) {
    return new CreateCommentCommand(
        CommentTargetType.POST, postId, null, userId, parentId, content);
  }

  public static CreateCommentCommand forAnswer(
      Long answerId, Long userId, Long parentId, String content) {
    return new CreateCommentCommand(
        CommentTargetType.ANSWER, null, answerId, userId, parentId, content);
  }

  private void validate(
      CommentTargetType targetType, Long postId, Long answerId, Long userId, String content) {
    boolean invalidPostTarget =
        CommentTargetType.POST.equals(targetType) && (postId == null || answerId != null);
    boolean invalidAnswerTarget =
        CommentTargetType.ANSWER.equals(targetType) && (postId != null || answerId == null);
    if (invalidPostTarget
        || invalidAnswerTarget
        || userId == null
        || content == null
        || content.isBlank()) {
      throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
    }
  }
}
