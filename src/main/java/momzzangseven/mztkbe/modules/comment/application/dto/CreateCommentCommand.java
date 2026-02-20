package momzzangseven.mztkbe.modules.comment.application.dto;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public record CreateCommentCommand(Long postId, Long userId, Long parentId, String content) {
  public CreateCommentCommand {
    validate(postId, userId, content);
  }

  private void validate(Long postId, Long userId, String content) {
    if (postId == null || userId == null || content == null || content.isBlank()) {
      throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
    }
    if (content.length() > 1000) {
      throw new BusinessException(ErrorCode.COMMENT_TOO_LONG);
    }
  }
}
